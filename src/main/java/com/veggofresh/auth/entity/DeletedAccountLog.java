package com.veggofresh.auth.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Permanent audit trail of every SELF-service account deletion -- written at the moment the delete
 * is confirmed (Day 0), BEFORE any scrubbing happens, so it captures the account's real
 * phone/email/name unmasked (product decision: admin must be able to identify who left, for
 * support / dispute / compliance purposes).
 *
 * <p>Deliberately NOT soft-delete-filtered and NEVER touched by the purge job -- this row outlives
 * the {@code users} row it was captured from, on purpose.
 *
 * <p>It also records what became of the account, so admin can tell the four outcomes apart:
 * <ul>
 *   <li>{@code reactivatedAt != null} -- the person came back inside the window (same user id);</li>
 *   <li>{@code purgedAt != null} -- the window ended and the account was scrubbed;</li>
 *   <li>neither -- still inside the reactivation window.</li>
 * </ul>
 * "Returned as a NEW account" is not stored: it is worked out by looking for an active user with
 * the same phone and role (see AdminDeletedAccountServiceImpl).
 */
@Entity
@Table(name = "deleted_account_log")
@Getter
@Setter
public class DeletedAccountLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "account_created_at", nullable = false)
    private Instant accountCreatedAt;

    @Column(name = "requested_retention_days", nullable = false)
    private int requestedRetentionDays;

    @Column(name = "scheduled_purge_at", nullable = false)
    private Instant scheduledPurgeAt;

    @Column(length = 500)
    private String reason;

    /** Always "SELF" -- admin-initiated deletion does not exist. */
    @Column(name = "initiated_by", nullable = false, length = 20)
    private String initiatedBy = "SELF";

    @Column(name = "total_orders")
    private Integer totalOrders;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Set when the person logs back in inside the window and the account is restored. */
    @Column(name = "reactivated_at")
    private Instant reactivatedAt;

    /** Set when the account's phone/email are scrubbed (end of the window, or an immediate delete). */
    @Column(name = "purged_at")
    private Instant purgedAt;
}
