package com.gharpahucho.integration.flagsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.entity.User;
import com.gharpahucho.integration.flagsmith.FeatureSnapshot.ConfigResult;
import com.gharpahucho.integration.flagsmith.FeatureSnapshot.FlagResult;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central place that answers "is this capability on for this user?" and "what are the tunables right now?".
 *
 * Source of truth is Flagsmith. When Flagsmith is unavailable the documented fallback defaults apply and every
 * result says so ({@code FALLBACK_DEFAULT}); the UI surfaces that on the feature demonstration screen.
 */
@Service
public class FeatureFlagService {

    private final GharPahuchoProperties props;
    private final FlagsmithGateway gateway;
    private final UserRepository users;
    private final TripRepository trips;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    private final Map<String, Cached<FeatureSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Cached<Identity>> identities = new ConcurrentHashMap<>();

    public FeatureFlagService(GharPahuchoProperties props, FlagsmithGateway gateway, UserRepository users,
                              TripRepository trips, ObjectMapper mapper, MeterRegistry meters) {
        this.props = props;
        this.gateway = gateway;
        this.users = users;
        this.trips = trips;
        this.mapper = mapper;
        this.meters = meters;
    }

    public FlagsmithGateway gateway() {
        return gateway;
    }

    public boolean isEnabled(FeatureKey key, UUID userId) {
        boolean enabled = snapshot(userId).enabled(key);
        meters.counter("gharpahucho.flag.evaluations", "flag", key.key(), "enabled", String.valueOf(enabled)).increment();
        return enabled;
    }

    public RuntimeConfig config(UUID userId) {
        return snapshot(userId).runtime();
    }

    /** Evaluates all flags and remote-config values for the user (or the environment when userId is null). */
    public FeatureSnapshot snapshot(UUID userId) {
        String cacheKey = userId == null ? "environment" : userId.toString();
        long ttl = gateway.mode().equals("REMOTE_EVALUATION") ? 4000 : 1000;
        Cached<FeatureSnapshot> cached = snapshots.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.at < ttl) {
            return cached.value;
        }
        FeatureSnapshot fresh = evaluate(userId);
        snapshots.put(cacheKey, new Cached<>(fresh));
        return fresh;
    }

    /** Call after the user changes anything that is sent to Flagsmith as a trait. */
    public void invalidate(UUID userId) {
        if (userId != null) {
            snapshots.remove(userId.toString());
            identities.remove(userId);
        }
    }

    private FeatureSnapshot evaluate(UUID userId) {
        Identity identity = userId == null ? new Identity(null, Map.of()) : identity(userId);
        Optional<Flags> flags = gateway.evaluate(identity.id(), identity.traits());

        Map<FeatureKey, FlagResult> flagResults = new EnumMap<>(FeatureKey.class);
        for (FeatureKey key : FeatureKey.values()) {
            boolean fallback = props.getFlagsmith().getDefaults().getOrDefault(key.key(), false);
            flagResults.put(key, flags.map(f -> readFlag(f, key.key(), fallback))
                    .orElseGet(() -> new FlagResult(fallback, FeatureSnapshot.SOURCE_FALLBACK,
                            gateway.isConfigured() ? "Flagsmith unreachable: safe default applied" : "Flagsmith not configured: safe default applied")));
        }

        Map<ConfigKey, ConfigResult> configResults = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : ConfigKey.values()) {
            String fallback = props.getFlagsmith().getConfigDefaults().getOrDefault(key.key(), "");
            configResults.put(key, flags.map(f -> readConfig(f, key.key(), fallback))
                    .orElseGet(() -> new ConfigResult(fallback, FeatureSnapshot.SOURCE_FALLBACK,
                            gateway.isConfigured() ? "Flagsmith unreachable: default applied" : "Flagsmith not configured: default applied")));
        }
        return new FeatureSnapshot(identity.id(), identity.traits(), flagResults, configResults,
                toRuntime(configResults), flags.isPresent(), Instant.now());
    }

    private FlagResult readFlag(Flags flags, String name, boolean fallback) {
        try {
            BaseFlag flag = flags.getFlag(name);
            if (flag == null || FlagsmithGateway.isDefault(flag)) {
                return new FlagResult(fallback, FeatureSnapshot.SOURCE_FALLBACK,
                        "Feature '" + name + "' does not exist in this Flagsmith environment: safe default applied");
            }
            String reason = flag.getReason();
            return new FlagResult(Boolean.TRUE.equals(flag.getEnabled()), FeatureSnapshot.SOURCE_FLAGSMITH,
                    reason == null || reason.isBlank() ? "Evaluated by Flagsmith" : reason);
        } catch (Exception e) {
            return new FlagResult(fallback, FeatureSnapshot.SOURCE_FALLBACK, "Evaluation error: safe default applied");
        }
    }

    private ConfigResult readConfig(Flags flags, String name, String fallback) {
        try {
            BaseFlag flag = flags.getFlag(name);
            if (flag == null || FlagsmithGateway.isDefault(flag)) {
                return new ConfigResult(fallback, FeatureSnapshot.SOURCE_FALLBACK, "Not defined in Flagsmith: default applied");
            }
            if (!Boolean.TRUE.equals(flag.getEnabled())) {
                return new ConfigResult(fallback, FeatureSnapshot.SOURCE_FALLBACK, "Feature disabled in Flagsmith: default applied");
            }
            Object value = flag.getValue();
            if (value == null || String.valueOf(value).isBlank()) {
                return new ConfigResult(fallback, FeatureSnapshot.SOURCE_FALLBACK, "No value set in Flagsmith: default applied");
            }
            return new ConfigResult(String.valueOf(value), FeatureSnapshot.SOURCE_FLAGSMITH, "Remote config value from Flagsmith");
        } catch (Exception e) {
            return new ConfigResult(fallback, FeatureSnapshot.SOURCE_FALLBACK, "Evaluation error: default applied");
        }
    }

    private RuntimeConfig toRuntime(Map<ConfigKey, ConfigResult> c) {
        RuntimeConfig d = RuntimeConfig.defaults();
        return new RuntimeConfig(
                RuntimeConfig.clamp(num(c, ConfigKey.MATCHING_RADIUS_KM, d.matchingRadiusKm()), 1, 500),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.DATE_FLEXIBILITY_DAYS, d.dateFlexibilityDays()), 0, 7),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.MAX_BUDGET_DEVIATION_PCT, d.maxBudgetDeviationPct()), 0, 200),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.MIN_COMPATIBILITY_SCORE, d.minCompatibilityScore()), 0, 100),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.MAX_RECOMMENDATIONS, d.maxRecommendations()), 1, 50),
                RuntimeConfig.MatchWeights.parse(c.get(ConfigKey.MATCHING_WEIGHTS).value(), mapper),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.RAIL_BOOKING_WINDOW_DAYS, d.railBookingWindowDays()), 1, 365),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.FREE_RADAR_TRIPS, d.freeRadarTrips()), 0, 50),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.FREE_RIDE_ALERTS, d.freeRideAlerts()), 0, 50),
                (int) RuntimeConfig.clamp(num(c, ConfigKey.FESTIVAL_PASS_PRICE_INR, d.passPriceInr()), 1, 100_000));
    }

    private static double num(Map<ConfigKey, ConfigResult> c, ConfigKey key, double fallback) {
        try {
            return Double.parseDouble(c.get(key).value().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Identity identity(UUID userId) {
        Cached<Identity> cached = identities.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.at < 15_000) {
            return cached.value;
        }
        Identity identity = users.findById(userId).map(this::toIdentity).orElse(new Identity(userId.toString(), Map.of()));
        identities.put(userId, new Cached<>(identity));
        return identity;
    }

    /**
     * Identity and traits sent to Flagsmith. These power segments such as "Hyderabad travellers" or "Early access"
     * and percentage rollouts. No e-mail, name or contact data is ever sent.
     */
    private Identity toIdentity(User user) {
        Map<String, Object> traits = new LinkedHashMap<>();
        traits.put("city", user.getHomeCity() == null ? "unknown" : user.getHomeCity());
        traits.put("preferred_language", user.getPreferredLanguage());
        traits.put("beta_tester", user.isBetaTester());
        traits.put("onboarded", user.isOnboarded());
        traits.put("demo_account", user.isDemoAccount());
        traits.put("plan", user.getPlan());
        traits.put("trips_created", trips.countByOwnerId(user.getId()));
        return new Identity(user.getId().toString(), traits);
    }

    private record Identity(String id, Map<String, Object> traits) {
    }

    private static final class Cached<T> {
        final T value;
        final long at = System.currentTimeMillis();

        Cached(T value) {
            this.value = value;
        }
    }
}
