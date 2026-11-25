package com.gharpahucho.integration.nudgebee;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.OpsDtos.IntegrationStatus;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Watches the real outcomes of third-party calls (never probes them, so no credits are spent) and turns a run of
 * consecutive failures into a NudgeBee alert, and the first success afterwards into the matching "resolved".
 * Also auto-resolves HTTP 5xx alerts that have been quiet for the de-duplication window.
 */
@Component
public class DependencyHealthMonitor {

    public static final String DEP_PREFIX = "gharpahucho:dependency:";
    public static final String HTTP_5XX_PREFIX = "gharpahucho:http-5xx:";

    private final IntegrationStatusRegistry registry;
    private final NudgeBeeEventPublisher publisher;
    private final GharPahuchoProperties.NudgeBee props;

    public DependencyHealthMonitor(IntegrationStatusRegistry registry, NudgeBeeEventPublisher publisher, GharPahuchoProperties properties) {
        this.registry = registry;
        this.publisher = publisher;
        this.props = properties.getNudgebee();
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void evaluate() {
        if (!props.isMonitorEnabled()) {
            return;
        }
        for (IntegrationStatus s : registry.snapshot(props.getFailureThreshold())) {
            if (IntegrationStatusRegistry.NUDGEBEE.equals(s.name()) || !s.configured()) {
                continue; // never report NudgeBee problems through NudgeBee itself
            }
            String fingerprint = DEP_PREFIX + slug(s.name());
            if ("DOWN".equals(s.status())) {
                publisher.fire(fingerprint, "dependency_unavailable",
                        s.name() + " is failing (" + s.consecutiveFailures() + " consecutive errors)",
                        "Last error: " + s.lastError() + ". GharPahucho is serving degraded results (documented fallbacks) while this persists.",
                        "high", s.name(), "dependency", Map.of("dependency", slug(s.name())), false);
            } else if ("UP".equals(s.status()) && publisher.isOpen(fingerprint)) {
                publisher.resolve(fingerprint);
            }
        }
        Instant quietBefore = Instant.now().minus(Duration.ofMinutes(props.getDedupeMinutes()));
        for (NudgeBeeEventPublisher.OpenEvent ev : publisher.openEvents()) {
            if (ev.autoResolve() && ev.lastSeen().isBefore(quietBefore)) {
                publisher.resolve(ev.fingerprint());
            }
        }
    }

    static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
