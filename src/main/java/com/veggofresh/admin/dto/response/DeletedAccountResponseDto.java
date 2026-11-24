package com.veggofresh.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One self-deleted account, as shown in the admin "Deleted accounts" archive.
 *
 * <p>{@code status}: IN_WINDOW (can still come back), REACTIVATED (came back, same account),
 * PURGED (gone, no return) or RETURNED_AS_NEW (gone, but the same phone + role has a NEW account now).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletedAccountResponseDto {
    private UUID id;
    /** The user id the account had when it was deleted. */
    private UUID userId;
    private String role;
    private String phone;
    private String email;
    private String fullName;
    private Instant accountCreatedAt;
    /** When the deletion was confirmed. */
    private Instant deletedAt;
    private int requestedRetentionDays;
    private Instant scheduledPurgeAt;
    private String reason;
    /** Always SELF. */
    private String initiatedBy;
    private Integer totalOrders;
    private BigDecimal totalAmount;

    private String status;
    private Instant reactivatedAt;
    private Instant purgedAt;

    /** RETURNED_AS_NEW only: the person came back under a new account. */
    private boolean returned;
    private UUID newUserId;
    private Instant returnedAt;

    /** How many times this phone + role has been self-deleted (including this one). */
    private int previousDeletions;
}
