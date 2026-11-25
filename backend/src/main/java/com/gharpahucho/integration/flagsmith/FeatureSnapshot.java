package com.gharpahucho.integration.flagsmith;

import java.time.Instant;
import java.util.Map;

/** Immutable result of evaluating every flag and config value for one identity at one moment. */
public record FeatureSnapshot(
        String identity,
        Map<String, Object> traits,
        Map<FeatureKey, FlagResult> flags,
        Map<ConfigKey, ConfigResult> config,
        RuntimeConfig runtime,
        boolean flagsmithAvailable,
        Instant evaluatedAt) {

    public static final String SOURCE_FLAGSMITH = "FLAGSMITH";
    public static final String SOURCE_FALLBACK = "FALLBACK_DEFAULT";

    public record FlagResult(boolean enabled, String source, String reason) {
    }

    public record ConfigResult(String value, String source, String reason) {
    }

    public boolean enabled(FeatureKey key) {
        FlagResult r = flags.get(key);
        return r != null && r.enabled();
    }
}
