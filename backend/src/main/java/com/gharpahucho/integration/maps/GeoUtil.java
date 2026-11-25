package com.gharpahucho.integration.maps;

public final class GeoUtil {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * Distance (km) from point P to the great-circle segment A-B, using a local equirectangular projection.
     * Accurate enough (a few percent) for the city-to-city corridors used by the experimental matcher.
     */
    public static double distanceToSegmentKm(double pLat, double pLon, double aLat, double aLon, double bLat, double bLon) {
        double refLat = Math.toRadians((aLat + bLat) / 2);
        double kx = 111.32 * Math.cos(refLat);
        double ky = 110.57;
        double ax = aLon * kx, ay = aLat * ky;
        double bx = bLon * kx, by = bLat * ky;
        double px = pLon * kx, py = pLat * ky;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    /** Fraction (0..1) of the way from A to B at which P projects onto the segment. */
    public static double projectionFraction(double pLat, double pLon, double aLat, double aLon, double bLat, double bLon) {
        double refLat = Math.toRadians((aLat + bLat) / 2);
        double kx = 111.32 * Math.cos(refLat);
        double ky = 110.57;
        double dx = (bLon - aLon) * kx, dy = (bLat - aLat) * ky;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            return 0;
        }
        double t = (((pLon - aLon) * kx) * dx + ((pLat - aLat) * ky) * dy) / len2;
        return Math.max(0, Math.min(1, t));
    }
}
