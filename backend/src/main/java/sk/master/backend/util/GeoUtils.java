package sk.master.backend.util;

public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {} // utility class

    /**
     * Haversine distance in meters.
     * Accurate for any two points on Earth (max error ~0.3% due to spherical model).
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Equirectangular approximation in meters.
     * Accurate to ~0.5% for distances < 10 km. ~3x faster than Haversine.
     * Suitable for high-frequency comparisons (filtering, spatial queries, clustering).
     */
    public static double equirectangularDistance(double lat1, double lon1, double lat2, double lon2) {
        double x = Math.toRadians(lon2 - lon1) * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double y = Math.toRadians(lat2 - lat1);
        return Math.sqrt(x * x + y * y) * EARTH_RADIUS_METERS;
    }

    /**
     * Počiatočný azimut (bearing) od bodu 1 k bodu 2 v stupňoch [0, 360).
     * Používa forward azimuth vzorec na sfére.
     */
    public static double initialBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    /**
     * Najmenší uhol medzi dvoma azimutmi v stupňoch [0, 180].
     */
    public static double bearingDifference(double b1, double b2) {
        double diff = Math.abs(b1 - b2) % 360;
        return diff > 180 ? 360 - diff : diff;
    }
}
