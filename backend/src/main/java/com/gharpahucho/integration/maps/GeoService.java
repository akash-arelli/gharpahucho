package com.gharpahucho.integration.maps;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * City lookup and routing. Order of truth: built-in gazetteer, then OpenStreetMap Nominatim (optional); routing via
 * OSRM or Google Routes, falling back to a clearly labelled straight-line estimate so the demo never breaks.
 */
@Slf4j
@Service
public class GeoService {

    private static final double ROAD_FACTOR = 1.28;
    private static final double AVG_ROAD_KMH = 52.0;
    private static final int MAX_GEOMETRY_POINTS = 140;
    private static final long FALLBACK_CACHE_MS = 60_000;

    private final CityGazetteer gazetteer;
    private final NominatimClient nominatim;
    private final IntegrationStatusRegistry registry;
    private final RoutingClient primary;
    private final Map<String, CachedRoute> routes = new ConcurrentHashMap<>();

    public GeoService(CityGazetteer gazetteer, NominatimClient nominatim, IntegrationStatusRegistry registry,
                      OsrmClient osrm, GoogleRoutesClient google, GharPahuchoProperties props) {
        this.gazetteer = gazetteer;
        this.nominatim = nominatim;
        this.registry = registry;
        boolean useGoogle = "google".equalsIgnoreCase(props.getMaps().getProvider()) && google.isAvailable();
        this.primary = useGoogle ? google : osrm;
        registry.register(IntegrationStatusRegistry.ROUTING, true,
                primary.engineName() + (useGoogle ? "" : " (" + props.getMaps().getOsrmBaseUrl() + ")"));
    }

    public CityGazetteer gazetteer() {
        return gazetteer;
    }

    public Optional<City> resolveCity(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Optional<City> exact = gazetteer.find(input);
        if (exact.isPresent()) {
            return exact;
        }
        String firstPart = input.split(",")[0].trim();
        Optional<City> partial = gazetteer.find(firstPart);
        if (partial.isPresent()) {
            return partial;
        }
        Optional<City> mention = gazetteer.mentions(input).stream().findFirst().map(CityGazetteer.Mention::city);
        if (mention.isPresent()) {
            return mention;
        }
        return nominatim.lookup(firstPart);
    }

    public City requireCity(String input, String field) {
        return resolveCity(input).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_CITY",
                "We could not find \"" + input + "\" as a city in India (" + field + "). Try a nearby major city."));
    }

    public RouteInfo route(City from, City to) {
        String key = from.name() + "|" + to.name();
        CachedRoute cached = routes.get(key);
        if (cached != null && (cached.measured || System.currentTimeMillis() - cached.at < FALLBACK_CACHE_MS)) {
            return cached.route;
        }
        RouteInfo fresh = compute(from, to);
        routes.put(key, new CachedRoute(fresh, fresh.measured(), System.currentTimeMillis()));
        return fresh;
    }

    private RouteInfo compute(City from, City to) {
        try {
            RoutingClient.EngineRoute r = primary.route(from.lat(), from.lon(), to.lat(), to.lon());
            registry.success(IntegrationStatusRegistry.ROUTING);
            return new RouteInfo(from, to, round1(r.distanceMeters() / 1000.0), (int) Math.round(r.durationSeconds() / 60.0),
                    primary.engineName(), true, "Distance and driving time computed by " + primary.engineName() + ".",
                    downsample(r.latLonGeometry()));
        } catch (Exception e) {
            registry.failure(IntegrationStatusRegistry.ROUTING, e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("event=routing_failed engine={} from={} to={} error={}", primary.engineName(), from.name(), to.name(), e.getMessage());
            double straight = GeoUtil.haversineKm(from.lat(), from.lon(), to.lat(), to.lon());
            double road = straight * ROAD_FACTOR;
            List<double[]> line = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                line.add(new double[]{from.lat() + (to.lat() - from.lat()) * t, from.lon() + (to.lon() - from.lon()) * t});
            }
            return new RouteInfo(from, to, round1(road), (int) Math.round(road / AVG_ROAD_KMH * 60), "ESTIMATED_STRAIGHT_LINE", false,
                    "Routing service unavailable: distance is a straight-line estimate (x" + ROAD_FACTOR + "), not a measured route.", line);
        }
    }

    private static List<double[]> downsample(List<double[]> pts) {
        if (pts.size() <= MAX_GEOMETRY_POINTS) {
            return pts;
        }
        List<double[]> out = new ArrayList<>(MAX_GEOMETRY_POINTS);
        double step = (pts.size() - 1) / (double) (MAX_GEOMETRY_POINTS - 1);
        for (int i = 0; i < MAX_GEOMETRY_POINTS; i++) {
            out.add(pts.get((int) Math.round(i * step)));
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private record CachedRoute(RouteInfo route, boolean measured, long at) {
    }
}
