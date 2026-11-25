package com.gharpahucho.integration.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gharpahucho.config.AppConfig;
import com.gharpahucho.config.GharPahuchoProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional Google Maps Routes API client (https://developers.google.com/maps/documentation/routes).
 * Enabled with MAPS_PROVIDER=google and GOOGLE_MAPS_API_KEY. Never required for the demo.
 */
@Component
public class GoogleRoutesClient implements RoutingClient {

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper mapper;

    public GoogleRoutesClient(GharPahuchoProperties props, ObjectMapper mapper) {
        this.apiKey = props.getMaps().getGoogleApiKey();
        this.mapper = mapper;
        this.http = AppConfig.restClient("https://routes.googleapis.com", props.getMaps().getTimeoutSeconds()).build();
    }

    @Override
    public String engineName() {
        return "GOOGLE_ROUTES";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public EngineRoute route(double fromLat, double fromLon, double toLat, double toLon) {
        ObjectNode body = mapper.createObjectNode();
        body.set("origin", waypoint(fromLat, fromLon));
        body.set("destination", waypoint(toLat, toLon));
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_UNAWARE");
        JsonNode res = http.post()
                .uri("/directions/v2:computeRoutes")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (res == null || !res.path("routes").has(0)) {
            throw new IllegalStateException("Google Routes returned no route");
        }
        JsonNode r = res.path("routes").get(0);
        double seconds = Double.parseDouble(r.path("duration").asText("0s").replace("s", ""));
        return new EngineRoute(r.path("distanceMeters").asDouble(), seconds, decodePolyline(r.path("polyline").path("encodedPolyline").asText("")));
    }

    private ObjectNode waypoint(double lat, double lon) {
        ObjectNode latLng = mapper.createObjectNode().put("latitude", lat).put("longitude", lon);
        ObjectNode location = mapper.createObjectNode();
        location.set("latLng", latLng);
        ObjectNode wp = mapper.createObjectNode();
        wp.set("location", location);
        return wp;
    }

    /** Standard Google encoded-polyline decoding (precision 1e5). */
    static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int shift = 0, result = 0, b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < encoded.length());
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < encoded.length());
            lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            points.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return points;
    }
}
