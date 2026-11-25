package com.gharpahucho.controller;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.OpsDtos.HealthResponse;
import com.gharpahucho.dto.OpsDtos.IntegrationStatus;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Human- and monitor-friendly health summary. Only the database decides UP/DOWN: third-party services degrade
 * gracefully (documented fallbacks), so their trouble is reported as component detail, not as an outage.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final IntegrationStatusRegistry registry;
    private final GharPahuchoProperties props;

    @Value("${GP_VERSION:0.1.0}")
    private String version;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        Map<String, String> components = new LinkedHashMap<>();
        boolean dbUp = databaseUp();
        components.put("database", dbUp ? "UP" : "DOWN");
        for (IntegrationStatus s : registry.snapshot(props.getNudgebee().getFailureThreshold())) {
            components.put(s.name(), s.status());
        }
        HealthResponse body = new HealthResponse(dbUp ? "UP" : "DOWN", "gharpahucho-backend", version, Instant.now(), components);
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean databaseUp() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
