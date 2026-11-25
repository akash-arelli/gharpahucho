package com.gharpahucho.integration.transport;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.CityGazetteer;
import com.gharpahucho.integration.maps.GeoUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * One-change journeys (A to hub, hub to B) assembled from the simulated inventory. Only used when the Flagsmith flag
 * {@code alternative_route_recommendations} is on. A connection needs a safe layover; the buffer is never shorter than
 * 90 minutes and the combined journey must stay within a sensible detour of the direct distance.
 */
@Component
public class ConnectionProvider {

    private static final Set<String> HUBS = Set.of("Hyderabad", "Bengaluru", "Chennai", "Mumbai", "Pune", "Delhi",
            "Vijayawada", "Kolkata", "Nagpur", "Ahmedabad", "Visakhapatnam");
    private static final int MIN_LAYOVER_MINUTES = 90;
    private static final int MAX_LAYOVER_MINUTES = 8 * 60;
    private static final double MAX_DETOUR = 1.35;
    private static final int MAX_RESULTS = 10;

    private final SimulatedInventoryProvider inventory;
    private final CityGazetteer gazetteer;

    public ConnectionProvider(SimulatedInventoryProvider inventory, CityGazetteer gazetteer) {
        this.inventory = inventory;
        this.gazetteer = gazetteer;
    }

    public List<Candidate> search(SearchContext ctx) {
        double direct = GeoUtil.haversineKm(ctx.from().lat(), ctx.from().lon(), ctx.to().lat(), ctx.to().lon());
        List<Candidate> out = new ArrayList<>();
        for (String hubName : HUBS) {
            if (hubName.equals(ctx.from().name()) || hubName.equals(ctx.to().name())) {
                continue;
            }
            City hub = gazetteer.findByName(hubName).orElse(null);
            if (hub == null) {
                continue;
            }
            double via = GeoUtil.haversineKm(ctx.from().lat(), ctx.from().lon(), hub.lat(), hub.lon())
                    + GeoUtil.haversineKm(hub.lat(), hub.lon(), ctx.to().lat(), ctx.to().lon());
            if (via > direct * MAX_DETOUR) {
                continue;
            }
            List<Candidate> first = inventory.searchBetween(ctx.from().name(), hubName, ctx.searchFrom(), ctx.searchTo(), ctx.now());
            List<Candidate> second = inventory.searchBetween(hubName, ctx.to().name(), ctx.searchFrom(), ctx.searchTo().plusDays(1), ctx.now());
            for (Candidate a : first) {
                second.stream()
                        .filter(b -> {
                            long layover = Duration.between(a.arrival(), b.departure()).toMinutes();
                            return layover >= MIN_LAYOVER_MINUTES && layover <= MAX_LAYOVER_MINUTES;
                        })
                        .min(Comparator.comparing(Candidate::arrival))
                        .ifPresent(b -> out.add(combine(a, b, hubName)));
            }
        }
        out.sort(Comparator.comparingInt(Candidate::durationMinutes));
        return out.stream().limit(MAX_RESULTS).toList();
    }

    private Candidate combine(Candidate a, Candidate b, String hub) {
        long layover = Duration.between(a.arrival(), b.departure()).toMinutes();
        LocalDateTime dep = a.departure();
        LocalDateTime arr = b.arrival();
        TransportMode mode = a.durationMinutes() >= b.durationMinutes() ? a.mode() : b.mode();
        List<Candidate.Leg> legs = new ArrayList<>(a.legs());
        legs.addAll(b.legs());
        String note = a.festiveNote() != null ? a.festiveNote() : b.festiveNote();
        return new Candidate("conn:" + a.id() + "+" + b.id(), mode,
                a.operator() + " + " + b.operator(),
                a.mode().label() + " to " + hub + ", then " + b.mode().label().toLowerCase(),
                dep, arr, a.costPerPerson() + b.costPerPerson(), 1,
                "1 change at " + hub + " (" + layover / 60 + "h " + layover % 60 + "m layover)",
                DataSource.SIMULATED_INVENTORY, "SAMPLE_UNVERIFIED",
                "Sample schedule: availability not verified, connection not guaranteed",
                Math.min(a.comfort(), b.comfort()), null, null, null, legs, note);
    }
}
