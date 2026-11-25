package com.gharpahucho.integration.flagsmith;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed view of the Flagsmith remote-config values after validation and clamping.
 * Bad or out-of-range values from Flagsmith can never break the algorithms: they are clamped to safe bounds.
 */
public record RuntimeConfig(
        double matchingRadiusKm,
        int dateFlexibilityDays,
        int maxBudgetDeviationPct,
        int minCompatibilityScore,
        int maxRecommendations,
        MatchWeights weights,
        int railBookingWindowDays,
        int freeRadarTrips,
        int freeRideAlerts,
        int passPriceInr) {

    public RuntimeConfig(double matchingRadiusKm, int dateFlexibilityDays, int maxBudgetDeviationPct, int minCompatibilityScore,
                         int maxRecommendations, MatchWeights weights) {
        this(matchingRadiusKm, dateFlexibilityDays, maxBudgetDeviationPct, minCompatibilityScore, maxRecommendations, weights, 60, 1, 1, 299);
    }

    /** Weights are normalised so they always sum to 1.0, whatever is configured in Flagsmith. */
    public record MatchWeights(double destination, double date, double start, double budget, double transport) {

        public static MatchWeights defaults() {
            return normalised(35, 25, 20, 10, 10);
        }

        public static MatchWeights normalised(double destination, double date, double start, double budget, double transport) {
            double d = Math.max(0, destination), da = Math.max(0, date), s = Math.max(0, start),
                    b = Math.max(0, budget), t = Math.max(0, transport);
            double sum = d + da + s + b + t;
            if (sum <= 0) {
                return defaults();
            }
            return new MatchWeights(d / sum, da / sum, s / sum, b / sum, t / sum);
        }

        public static MatchWeights parse(String json, ObjectMapper mapper) {
            if (json == null || json.isBlank()) {
                return defaults();
            }
            try {
                JsonNode n = mapper.readTree(json);
                return normalised(n.path("destination").asDouble(35), n.path("date").asDouble(25),
                        n.path("start").asDouble(20), n.path("budget").asDouble(10), n.path("transport").asDouble(10));
            } catch (Exception e) {
                return defaults();
            }
        }

        public Map<String, Integer> asPercentages() {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put("destination", (int) Math.round(destination * 100));
            m.put("date", (int) Math.round(date * 100));
            m.put("start", (int) Math.round(start * 100));
            m.put("budget", (int) Math.round(budget * 100));
            m.put("transport", (int) Math.round(transport * 100));
            return m;
        }
    }

    public static RuntimeConfig defaults() {
        return new RuntimeConfig(25, 2, 20, 40, 12, MatchWeights.defaults(), 60, 1, 1, 299);
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
