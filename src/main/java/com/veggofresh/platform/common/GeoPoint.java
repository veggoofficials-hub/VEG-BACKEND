package com.veggofresh.platform.common;

import com.veggofresh.platform.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A validated latitude/longitude pair. Controllers build one with {@link #of(double, double)} so a
 * request such as {@code latitude=500} is rejected with a clear 400 instead of silently matching
 * nothing.
 */
public record GeoPoint(double latitude, double longitude) {

    /** True when both values are real numbers inside the valid ranges (-90..90 and -180..180). */
    public static boolean isValid(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    /** Validates and wraps the pair, or throws 400 {@code INVALID_COORDINATES}. */
    public static GeoPoint of(double latitude, double longitude) {
        if (!isValid(latitude, longitude)) {
            throw new BusinessException("INVALID_COORDINATES",
                    "latitude must be between -90 and 90 and longitude between -180 and 180",
                    HttpStatus.BAD_REQUEST);
        }
        return new GeoPoint(latitude, longitude);
    }

    /** Same, rounded to about 100 m. Used for guest (public) requests so their results can be cached safely. */
    public GeoPoint roundedToGrid() {
        return new GeoPoint(Math.round(latitude * 1000.0) / 1000.0, Math.round(longitude * 1000.0) / 1000.0);
    }
}
