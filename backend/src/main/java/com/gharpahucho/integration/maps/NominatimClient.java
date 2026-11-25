package com.gharpahucho.integration.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.gharpahucho.config.AppConfig;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenStreetMap Nominatim geocoder for cities that are not in the built-in gazetteer.
 * Respects the usage policy: identifying User-Agent, India-only, results cached in memory, no autocomplete traffic.
 */
@Slf4j
@Component
public class NominatimClient {

    private final GharPahuchoProperties.Maps props;
    private final RestClient http;
    private final IntegrationStatusRegistry registry;
    private final Map<String, Optional<City>> cache = new ConcurrentHashMap<>();

    public NominatimClient(GharPahuchoProperties properties, IntegrationStatusRegistry registry) {
        this.props = properties.getMaps();
        this.registry = registry;
        this.http = AppConfig.restClient(props.getNominatimBaseUrl(), props.getTimeoutSeconds())
                .defaultHeader("User-Agent", props.getUserAgent())
                .build();
        registry.register(IntegrationStatusRegistry.GEOCODING, props.isNominatimEnabled(),
                props.isNominatimEnabled() ? "Used only for cities outside the built-in list" : "Disabled (NOMINATIM_ENABLED=false)");
    }

    public Optional<City> lookup(String query) {
        if (!props.isNominatimEnabled() || query == null || query.isBlank() || query.length() > 80) {
            return Optional.empty();
        }
        String key = query.trim().toLowerCase();
        return cache.computeIfAbsent(key, k -> fetch(query.trim()));
    }

    private Optional<City> fetch(String query) {
        try {
            JsonNode res = http.get()
                    .uri(u -> u.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "in")
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            registry.success(IntegrationStatusRegistry.GEOCODING);
            if (res == null || !res.has(0)) {
                return Optional.empty();
            }
            JsonNode hit = res.get(0);
            JsonNode addr = hit.path("address");
            String name = firstNonBlank(addr.path("city").asText(null), addr.path("town").asText(null),
                    addr.path("village").asText(null), addr.path("county").asText(null), hit.path("name").asText(null));
            if (name == null) {
                return Optional.empty();
            }
            return Optional.of(new City(name, addr.path("state").asText(""), hit.path("lat").asDouble(), hit.path("lon").asDouble()));
        } catch (Exception e) {
            registry.failure(IntegrationStatusRegistry.GEOCODING, e.getMessage());
            log.warn("event=nominatim_lookup_failed error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
