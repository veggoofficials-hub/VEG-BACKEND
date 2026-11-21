package com.veggofresh.vendor.dto;

/** Whether the app can deliver to a given location. */
public enum ServiceabilityStatus {

    /** At least one approved, online shop in range has at least one listed product. */
    SERVICEABLE,

    /** Approved shops exist in range, but none is both online and has a listed product right now. */
    TEMPORARILY_UNAVAILABLE,

    /** No approved shop is within the delivery radius of this location at all. */
    NOT_SERVICEABLE
}
