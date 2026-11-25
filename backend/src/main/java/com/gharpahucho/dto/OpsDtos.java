package com.gharpahucho.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OpsDtos {
    private OpsDtos() {
    }

    public record IntegrationStatus(
            String name,
            boolean configured,
            /** UP, DEGRADED, DOWN or NOT_CONFIGURED. */
            String status,
            long totalCalls,
            long failedCalls,
            int consecutiveFailures,
            Instant lastSuccess,
            Instant lastFailure,
            String lastError,
            String detail) {
    }

    public record NudgeBeeStatus(
            boolean webhookConfigured,
            boolean graphqlConfigured,
            long eventsSent,
            long eventsFailed,
            Instant lastEventAt,
            String lastEventResult,
            List<String> openFingerprints,
            String docs) {
    }

    public record OpsStatus(
            String service,
            String version,
            String environment,
            boolean demoMode,
            List<IntegrationStatus> integrations,
            NudgeBeeStatus nudgebee,
            Map<String, String> endpoints,
            Instant checkedAt) {
    }

    public record HealthResponse(String status, String service, String version, Instant time,
                                 Map<String, String> components) {
    }
}
