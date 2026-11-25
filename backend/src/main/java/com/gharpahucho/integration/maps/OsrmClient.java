package com.gharpahucho.integration.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.gharpahucho.config.AppConfig;
import com.gharpahucho.config.GharPahuchoProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OSRM route service client (https://project-osrm.org/docs/). Uses the public demo server by default, which is fine for
 * a demo but not for production traffic: point OSRM_BASE_URL at your own instance for anything beyond that.
 */
@Component
public class OsrmClient implements RoutingClient {

    private final RestClient http;

    public OsrmClient(GharPahuchoProperties props) {
        GharPahuchoProperties.Maps maps = props.getMaps();
        this.http = AppConfig.restClient(maps.getOsrmBaseUrl(), maps.getTimeoutSeconds())
                .defaultHeader("User-Agent", maps.getUserAgent())
                .build();
    }

    @Override
    public String engineName() {
        return "OSRM";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public EngineRoute route(double fromLat, double fromLon, double toLat, double toLon) {
        String coords = String.format(Locale.ROOT, "%.5f,%.5f;%.5f,%.5f", fromLon, fromLat, toLon, toLat);
        JsonNode body = http.get()
                .uri("/route/v1/driving/" + coords + "?overview=simplified&geometries=geojson&steps=false")
                .retrieve()
                .body(JsonNode.class);
        if (body == null || !"Ok".equals(body.path("code").asText()) || !body.path("routes").has(0)) {
            throw new IllegalStateException("OSRM returned no route: " + (body == null ? "empty body" : body.path("code").asText()));
        }
        JsonNode route = body.path("routes").get(0);
        List<double[]> geometry = new ArrayList<>();
        for (JsonNode c : route.path("geometry").path("coordinates")) {
            geometry.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()});
        }
        return new EngineRoute(route.path("distance").asDouble(), route.path("duration").asDouble(), geometry);
    }
}
