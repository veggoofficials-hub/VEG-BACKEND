package com.veggofresh.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Who a deleted/purged account was, as captured in the permanent deletion log BEFORE the live row was scrubbed.
 * {@code status} is DELETED (still inside the reactivation window) or PURGED.
 */
public record DeletedAccountIdentity(
        UUID userId,
        String role,
        String phone,
        String email,
        String fullName,
        String status,
        Instant deletedAt,
        Instant purgedAt) {

    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_PURGED = "PURGED";
}
