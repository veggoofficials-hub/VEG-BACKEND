package com.veggofresh.platform.common;

/**
 * The ONE place the great-circle (haversine) distance is worked out.
 *
 * <p>Before this class existed the customer catalog ({@code ProductCatalogServiceImpl}) and the
 * delivery assignment code each carried their own copy of the formula. Both now call this.
 */
public final class GeoDistance {

    private static final int EARTH_RADIUS_KM = 6371;

    private GeoDistance() {
    }

    /** Exact straight-line distance in kilometres. Used for rider matching. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Straight-line distance rounded to one decimal place (0.1 km). This is the value the customer
     * catalog compares against the delivery radius and shows in the app, exactly as before.
     */
    public static double distanceKmRounded(double lat1, double lon1, double lat2, double lon2) {
        return Math.round(haversineKm(lat1, lon1, lat2, lon2) * 10.0) / 10.0;
    }
}
