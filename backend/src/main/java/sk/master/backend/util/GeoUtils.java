package sk.master.backend.util;

public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {} // utility class

    /**
     * Haversine vzdialenosť v metroch.
     * Presná pre akékoľvek dva body na Zemi (max chyba ~0.3% kvôli sférickému modelu).
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
     * Equirectangular approximácia v metroch.
     * Presná do ~0.5% pre vzdialenosti < 10 km. ~3× rýchlejšia ako Haversine.
     * Vhodná pre high-frequency porovnávania (filtering, spatial queries, clustering).
     */
    public static double equirectangularDistance(double lat1, double lon1, double lat2, double lon2) {
        double x = Math.toRadians(lon2 - lon1) * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double y = Math.toRadians(lat2 - lat1);
        return Math.sqrt(x * x + y * y) * EARTH_RADIUS_METERS;
    }
}
