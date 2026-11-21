package com.veggofresh.platform.common;

import com.veggofresh.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoPointTest {

    @Test
    @DisplayName("Valid coordinates are accepted, including the extremes")
    void acceptsValidCoordinates() {
        assertTrue(GeoPoint.isValid(12.9716, 77.5946));
        assertTrue(GeoPoint.isValid(-90, -180));
        assertTrue(GeoPoint.isValid(90, 180));
        assertEquals(12.9716, GeoPoint.of(12.9716, 77.5946).latitude());
    }

    @Test
    @DisplayName("Out-of-range and non-numeric coordinates are rejected with INVALID_COORDINATES")
    void rejectsInvalidCoordinates() {
        assertFalse(GeoPoint.isValid(500, 10));
        assertFalse(GeoPoint.isValid(10, 181));
        assertFalse(GeoPoint.isValid(Double.NaN, 10));
        BusinessException ex = assertThrows(BusinessException.class, () -> GeoPoint.of(91, 0));
        assertEquals("INVALID_COORDINATES", ex.getErrorCode());
    }

    @Test
    @DisplayName("Rounding to the guest grid keeps about 100 m of precision")
    void roundsToGrid() {
        GeoPoint rounded = GeoPoint.of(12.971649, 77.594612).roundedToGrid();
        assertEquals(12.972, rounded.latitude(), 1e-9);
        assertEquals(77.595, rounded.longitude(), 1e-9);
    }

    @Test
    @DisplayName("Distance is symmetric, zero for the same point, and the rounded form uses one decimal")
    void distanceBasics() {
        assertEquals(0.0, GeoDistance.haversineKm(12.9, 77.5, 12.9, 77.5), 1e-9);
        double ab = GeoDistance.haversineKm(12.9716, 77.5946, 13.0827, 80.2707);
        double ba = GeoDistance.haversineKm(13.0827, 80.2707, 12.9716, 77.5946);
        assertEquals(ab, ba, 1e-9);
        double rounded = GeoDistance.distanceKmRounded(12.9716, 77.5946, 13.0827, 80.2707);
        assertEquals(Math.round(ab * 10.0) / 10.0, rounded, 1e-9);
    }
}
