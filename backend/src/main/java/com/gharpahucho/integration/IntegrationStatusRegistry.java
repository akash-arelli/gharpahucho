package com.gharpahucho.integration;

import com.gharpahucho.dto.OpsDtos.IntegrationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the real outcome of every call made to a third-party dependency (Flagsmith, Sarvam, OSRM, NudgeBee ...).
 * Nothing here is simulated: the numbers are driven only by actual calls. They feed the Ops panel, the Prometheus
 * metric {@code gharpahucho_integration_calls_total} and the NudgeBee dependency monitor.
 */
@Component
public class IntegrationStatusRegistry {

    public static final String FLAGSMITH = "Flagsmith";
    public static final String SARVAM = "Sarvam AI";
    public static final String ROUTING = "Routing (OSRM / Google)";
    public static final String GEOCODING = "OpenStreetMap Nominatim";
    public static final String NUDGEBEE = "NudgeBee webhook";

    private final MeterRegistry meters;
    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();

    public IntegrationStatusRegistry(MeterRegistry meters) {
        this.meters = meters;
    }

    /** Declares an integration (so it is listed even before its first call). */
    public void register(String name, boolean configured, String detail) {
        Tracker t = trackers.computeIfAbsent(name, n -> new Tracker());
        t.configured = configured;
        t.detail = detail;
    }

    public void success(String name) {
        Tracker t = trackers.computeIfAbsent(name, n -> new Tracker());
        t.total.incrementAndGet();
        t.consecutiveFailures.set(0);
        t.lastSuccess.set(Instant.now());
        meters.counter("gharpahucho.integration.calls", "integration", name, "outcome", "success").increment();
    }

    public void failure(String name, String error) {
        Tracker t = trackers.computeIfAbsent(name, n -> new Tracker());
        t.total.incrementAndGet();
        t.failed.incrementAndGet();
        t.consecutiveFailures.incrementAndGet();
        t.lastFailure.set(Instant.now());
        t.lastError.set(error == null ? "unknown error" : truncate(error));
        meters.counter("gharpahucho.integration.calls", "integration", name, "outcome", "failure").increment();
    }

    public int consecutiveFailures(String name) {
        Tracker t = trackers.get(name);
        return t == null ? 0 : t.consecutiveFailures.get();
    }

    public boolean isConfigured(String name) {
        Tracker t = trackers.get(name);
        return t != null && t.configured;
    }

    public String lastError(String name) {
        Tracker t = trackers.get(name);
        return t == null ? null : t.lastError.get();
    }

    public List<IntegrationStatus> snapshot(int failureThreshold) {
        List<IntegrationStatus> out = new ArrayList<>();
        trackers.forEach((name, t) -> {
            String status;
            int cf = t.consecutiveFailures.get();
            if (!t.configured) {
                status = "NOT_CONFIGURED";
            } else if (cf >= failureThreshold) {
                status = "DOWN";
            } else if (cf > 0) {
                status = "DEGRADED";
            } else {
                status = "UP";
            }
            out.add(new IntegrationStatus(name, t.configured, status, t.total.get(), t.failed.get(), cf,
                    t.lastSuccess.get(), t.lastFailure.get(), t.lastError.get(), t.detail));
        });
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    private static String truncate(String s) {
        return s.length() > 240 ? s.substring(0, 240) + "…" : s;
    }

    private static final class Tracker {
        volatile boolean configured = true;
        volatile String detail = "";
        final AtomicLong total = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicInteger consecutiveFailures = new AtomicInteger();
        final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
        final AtomicReference<Instant> lastFailure = new AtomicReference<>();
        final AtomicReference<String> lastError = new AtomicReference<>();
    }
}
