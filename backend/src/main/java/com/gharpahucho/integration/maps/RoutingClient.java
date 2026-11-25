package com.gharpahucho.integration.maps;

import java.util.List;

/** Abstraction over road-routing engines so OSRM and Google Routes are interchangeable. */
public interface RoutingClient {

    String engineName();

    boolean isAvailable();

    /** Raw engine result. Implementations throw on any failure; {@link GeoService} owns fallback behaviour. */
    EngineRoute route(double fromLat, double fromLon, double toLat, double toLon);

    record EngineRoute(double distanceMeters, double durationSeconds, List<double[]> latLonGeometry) {
    }
}
