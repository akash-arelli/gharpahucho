package com.gharpahucho.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FeatureDtos {
    private FeatureDtos() {
    }

    public record FeatureState(
            String key,
            boolean enabled,
            /** FLAGSMITH or FALLBACK_DEFAULT. */
            String source,
            String title,
            String description,
            /** Plain-language description of what the product does differently when this flag changes. */
            String effect) {
    }

    public record ConfigValue(
            String key,
            String value,
            String source,
            String title,
            String description) {
    }

    public record FlagsmithStatus(
            boolean configured,
            /** LOCAL_EVALUATION, REMOTE_EVALUATION or NOT_CONFIGURED. */
            String mode,
            String apiUrl,
            boolean healthy,
            Instant lastSuccess,
            Instant lastFailure,
            String lastError,
            int refreshIntervalSeconds) {
    }

    public record FeaturesResponse(
            /** Simple key -> enabled map for the UI to gate on. */
            Map<String, Boolean> flags,
            Map<String, String> config,
            List<FeatureState> states,
            List<ConfigValue> configStates,
            Map<String, Object> traits,
            String identity,
            FlagsmithStatus flagsmith,
            Instant evaluatedAt) {
    }
}
