package com.veggofresh.vendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Answer to "do you deliver here?" for one location. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceabilityDto {

    private ServiceabilityStatus status;

    /** Convenience flag: true only when {@code status == SERVICEABLE}. */
    private boolean serviceable;

    /** A ready-to-show message for the app. */
    private String message;

    /** The delivery radius in kilometres that was applied. */
    private double radiusKm;

    /** Distance in km to the nearest approved shop (online or not), or null when there is no approved shop at all. */
    private Double nearestShopDistanceKm;

    /** Approved shops within the radius, whether or not they are online. */
    private int shopsInRange;

    /** Shops within the radius that are online and have at least one listed product (the ones that can serve now). */
    private int servingShops;

    /** Echoed back so the app can match a response to the location it asked about. */
    private double latitude;

    private double longitude;
}
