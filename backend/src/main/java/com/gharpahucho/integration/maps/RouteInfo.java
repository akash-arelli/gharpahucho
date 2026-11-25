package com.gharpahucho.integration.maps;

import java.util.List;

/**
 * Road route between two cities.
 *
 * @param measured true only when a routing engine (OSRM / Google Routes) produced the figures. When it is false the
 *                 numbers are a straight-line estimate and every consumer must say so.
 * @param geometry [lat, lon] pairs, downsampled for the map.
 */
public record RouteInfo(
        City from,
        City to,
        double distanceKm,
        int durationMinutes,
        String engine,
        boolean measured,
        String note,
        List<double[]> geometry) {
}
