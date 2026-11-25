package com.gharpahucho.controller;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.OpsDtos.OpsStatus;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import com.gharpahucho.integration.nudgebee.NudgeBeeEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Operational visibility for the feature-demonstration screen and for NudgeBee setup checks. Signed-in users only. */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final IntegrationStatusRegistry registry;
    private final NudgeBeeEventPublisher nudgebee;
    private final GharPahuchoProperties props;

    @Value("${GP_VERSION:0.1.0}")
    private String version;

    @GetMapping("/status")
    public OpsStatus status() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("health", "/api/health");
        endpoints.put("liveness", "/actuator/health/liveness");
        endpoints.put("readiness", "/actuator/health/readiness");
        endpoints.put("metrics", "/actuator/prometheus");
        endpoints.put("apiDocs", "/swagger-ui.html");
        return new OpsStatus("gharpahucho-backend", version, props.getNudgebee().getEnvironment(), props.getDemo().isEnabled(),
                registry.snapshot(props.getNudgebee().getFailureThreshold()), nudgebee.status(), endpoints, Instant.now());
    }

    /**
     * Sends a real test event to the configured NudgeBee Event Webhook (or explains that none is configured).
     * {@code resolve=true} sends the matching "resolved" event for the open test alert.
     */
    @PostMapping("/nudgebee/test")
    public NudgeBeeEventPublisher.SendResult test(@RequestParam(defaultValue = "false") boolean resolve) {
        return nudgebee.sendTest(resolve);
    }
}
