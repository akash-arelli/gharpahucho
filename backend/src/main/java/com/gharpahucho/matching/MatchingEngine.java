package com.gharpahucho.matching;

import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.integration.maps.GeoUtil;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Deterministic traveller-compatibility scoring (0-100). No LLM is involved.
 *
 * <p>Five similarity components (each 0..1) are combined with weights that come from Flagsmith remote config
 * (default destination 35 / date 25 / start 20 / budget 10 / transport 10, always normalised to 100):
 * <ul>
 *   <li><b>destination</b>: same city, or proximity within the matching radius</li>
 *   <li><b>date</b>: same day, or within the combined date flexibility of both travellers plus the remote-config buffer</li>
 *   <li><b>start</b>: distance between starting points (locality-level when known)</li>
 *   <li><b>budget</b>: relative difference, tolerance from remote config</li>
 *   <li><b>transport</b>: preferred modes and willingness to share / seats on offer</li>
 * </ul>
 * The experimental v2 algorithm (Flagsmith flag {@code experimental_matching_algorithm}) is route-corridor aware: a traveller
 * heading to a city that lies along your road, or starting on your way, scores well even if cities differ.
 */
@Component
public class MatchingEngine {

    public static final String V1 = "v1-weighted";
    public static final String V2 = "v2-corridor (experimental)";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public record Params(RuntimeConfig config, boolean sharedRidesEnabled, boolean experimental) {
    }

    public record Result(Trip candidate, int score, Map<String, Integer> breakdown, List<String> reasons, String algorithm) {
    }

    /** Returns empty when the two trips cannot sensibly travel together (different day range, far-apart start or destination). */
    public Optional<Result> score(Trip seeker, Trip other, Params p) {
        RuntimeConfig cfg = p.config();
        double radius = cfg.matchingRadiusKm();
        List<String> reasons = new ArrayList<>();

        double dest = destination(seeker, other, radius, p.experimental(), reasons);
        double date = date(seeker, other, cfg.dateFlexibilityDays(), p.experimental(), reasons);
        double start = start(seeker, other, radius, p.experimental(), reasons);

        double startKm = GeoUtil.haversineKm(seeker.getSourceLat(), seeker.getSourceLon(), other.getSourceLat(), other.getSourceLon());
        boolean startReachable = startKm <= radius * 4 || (p.experimental() && start > 0);
        if (dest <= 0 || date <= 0 || !startReachable) {
            return Optional.empty();
        }
        double budget = budget(seeker, other, cfg.maxBudgetDeviationPct(), reasons);
        double transport = transport(seeker, other, p.sharedRidesEnabled(), p.experimental(), reasons);

        RuntimeConfig.MatchWeights w = cfg.weights();
        double total = w.destination() * dest + w.date() * date + w.start() * start + w.budget() * budget + w.transport() * transport;
        int score = (int) Math.round(Math.max(0, Math.min(1, total)) * 100);

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("destination", pct(dest));
        breakdown.put("date", pct(date));
        breakdown.put("start", pct(start));
        breakdown.put("budget", pct(budget));
        breakdown.put("transport", pct(transport));
        return Optional.of(new Result(other, score, breakdown, reasons, p.experimental() ? V2 : V1));
    }

    // ------------------------------------------------------------------ components

    private double destination(Trip a, Trip b, double radius, boolean v2, List<String> reasons) {
        if (a.getDestinationCity().equalsIgnoreCase(b.getDestinationCity())) {
            reasons.add("Same destination: " + b.getDestinationCity());
            return 1.0;
        }
        double km = GeoUtil.haversineKm(a.getDestinationLat(), a.getDestinationLon(), b.getDestinationLat(), b.getDestinationLon());
        double near = Math.max(0, 1 - km / radius);
        if (near > 0) {
            reasons.add("Nearby destination: " + b.getDestinationCity() + " is " + Math.round(km) + " km from " + a.getDestinationCity());
        }
        if (!v2) {
            return near;
        }
        double corridor = corridorDestination(a, b, radius, reasons);
        return Math.max(near, corridor);
    }

    /** v2: the other traveller's destination lies on my road (or mine on theirs), well past the halfway point. */
    private double corridorDestination(Trip a, Trip b, double radius, List<String> reasons) {
        double cross = GeoUtil.distanceToSegmentKm(b.getDestinationLat(), b.getDestinationLon(),
                a.getSourceLat(), a.getSourceLon(), a.getDestinationLat(), a.getDestinationLon());
        double frac = GeoUtil.projectionFraction(b.getDestinationLat(), b.getDestinationLon(),
                a.getSourceLat(), a.getSourceLon(), a.getDestinationLat(), a.getDestinationLon());
        if (cross <= radius && frac >= 0.5) {
            reasons.add("On your way: " + b.getDestinationCity() + " lies along the road to " + a.getDestinationCity());
            return 0.9 * (1 - cross / radius) * (0.6 + 0.4 * frac);
        }
        double cross2 = GeoUtil.distanceToSegmentKm(a.getDestinationLat(), a.getDestinationLon(),
                b.getSourceLat(), b.getSourceLon(), b.getDestinationLat(), b.getDestinationLon());
        double frac2 = GeoUtil.projectionFraction(a.getDestinationLat(), a.getDestinationLon(),
                b.getSourceLat(), b.getSourceLon(), b.getDestinationLat(), b.getDestinationLon());
        if (cross2 <= radius && frac2 >= 0.5) {
            reasons.add("On their way: " + a.getDestinationCity() + " lies along the road to " + b.getDestinationCity());
            return 0.9 * (1 - cross2 / radius) * (0.6 + 0.4 * frac2);
        }
        return 0;
    }

    private double date(Trip a, Trip b, int configFlex, boolean v2, List<String> reasons) {
        long diff = Math.abs(ChronoUnit.DAYS.between(a.getTravelDate(), b.getTravelDate()));
        if (diff == 0) {
            reasons.add("Travelling on the same date (" + DAY.format(a.getTravelDate()) + ")");
            return v2 ? timeBonus(a, b, 1.0) : 1.0;
        }
        long allowed = a.getFlexDays() + b.getFlexDays() + configFlex;
        if (diff > allowed) {
            return 0;
        }
        double s;
        if (v2) {
            double sigma = (allowed + 1) / 2.0;
            s = Math.exp(-(diff * diff) / (2 * sigma * sigma));
        } else {
            s = Math.max(0, 1 - diff / (double) (allowed + 1));
        }
        reasons.add("Dates within " + diff + " day" + (diff == 1 ? "" : "s") + " (" + DAY.format(b.getTravelDate()) + " vs " + DAY.format(a.getTravelDate()) + ")");
        return s;
    }

    private double timeBonus(Trip a, Trip b, double base) {
        if (a.getDepartureTime() == null || b.getDepartureTime() == null) {
            return base;
        }
        long minutes = Math.abs(ChronoUnit.MINUTES.between(a.getDepartureTime(), b.getDepartureTime()));
        return minutes <= 120 ? base : Math.max(0.75, base - (minutes - 120) / 1440.0);
    }

    private double start(Trip a, Trip b, double radius, boolean v2, List<String> reasons) {
        double km = GeoUtil.haversineKm(a.getSourceLat(), a.getSourceLon(), b.getSourceLat(), b.getSourceLon());
        boolean sameLocality = a.getSourceLocality() != null && a.getSourceLocality().equalsIgnoreCase(b.getSourceLocality());
        double s = Math.max(0, 1 - km / radius);
        if (sameLocality) {
            reasons.add("Both start from " + a.getSourceLocality());
            return 1.0;
        }
        if (km <= radius) {
            reasons.add("Nearby starting location (about " + Math.max(1, Math.round(km)) + " km apart)");
            return s;
        }
        if (v2) {
            double cross = GeoUtil.distanceToSegmentKm(b.getSourceLat(), b.getSourceLon(),
                    a.getSourceLat(), a.getSourceLon(), a.getDestinationLat(), a.getDestinationLon());
            double frac = GeoUtil.projectionFraction(b.getSourceLat(), b.getSourceLon(),
                    a.getSourceLat(), a.getSourceLon(), a.getDestinationLat(), a.getDestinationLon());
            if (cross <= radius && frac <= 0.4) {
                reasons.add("Starts on your way: " + b.getSourceCity() + " is along your route");
                return 0.7 * (1 - cross / radius);
            }
        }
        return 0;
    }

    private double budget(Trip a, Trip b, int allowedPct, List<String> reasons) {
        Integer x = a.getMaxBudget(), y = b.getMaxBudget();
        if (x == null || y == null) {
            return x == null && y == null ? 0.7 : 0.6;
        }
        double dev = Math.abs(x - y) / (double) Math.max(x, y) * 100.0;
        double s = Math.max(0, 1 - dev / Math.max(1.0, allowedPct * 2.5));
        if (dev <= allowedPct) {
            reasons.add("Similar budget (₹" + y + " vs ₹" + x + ")");
        }
        return s;
    }

    private double transport(Trip a, Trip b, boolean sharedRides, boolean v2, List<String> reasons) {
        TransportMode ma = a.getPreferredMode(), mb = b.getPreferredMode();
        double modeSim;
        if (ma == mb && ma != TransportMode.ANY) {
            modeSim = 1.0;
            reasons.add("Both prefer " + ma.label().toLowerCase());
        } else if (ma == TransportMode.ANY || mb == TransportMode.ANY) {
            modeSim = 0.8;
        } else if (isRoad(ma) && isRoad(mb)) {
            modeSim = 0.9;
        } else {
            modeSim = 0.2;
        }
        double shareSim = 0.5;
        if (sharedRides) {
            boolean offers = b.getSeatsAvailable() > 0;
            boolean fits = b.getSeatsAvailable() >= a.getTravellers();
            boolean wants = a.isWillingToShare() || (a.getSeatsAvailable() == 0);
            if (offers && fits && wants) {
                shareSim = 1.0;
                reasons.add(b.getSeatsAvailable() + " free seat" + (b.getSeatsAvailable() == 1 ? "" : "s")
                        + " for your party of " + a.getTravellers());
            } else if (a.isWillingToShare() && b.isWillingToShare()) {
                shareSim = 0.75;
                reasons.add("Both open to sharing a ride");
            } else if (a.isWillingToShare() || b.isWillingToShare()) {
                shareSim = 0.4;
            } else {
                shareSim = 0.2;
            }
            if (v2 && offers && !fits) {
                shareSim = Math.min(shareSim, 0.5);
            }
        }
        return 0.6 * modeSim + 0.4 * shareSim;
    }

    private static boolean isRoad(TransportMode m) {
        return m == TransportMode.CAB || m == TransportMode.SHARED_CAR;
    }

    private static int pct(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 100);
    }
}
