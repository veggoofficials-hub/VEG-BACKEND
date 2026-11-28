package com.veggofresh.payment.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A vendor or delivery partner's request to withdraw funds from their wallet
 * to their bank account. The actual bank transfer uses Razorpay Payout/Route
 * (gated behind {@link com.veggofresh.payment.config.RazorpayProperties#isPayoutsEnabled()}),
 * which requires KYC activation. Until KYC is done, requests go through an
 * admin approval queue and manual bank transfers outside the system.
 */
@Entity
@Table(name = "payout_requests")
@Getter
@Setter
@Where(clause = "deleted_at IS NULL")
public class PayoutRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutRequestStatus status = PayoutRequestStatus.PENDING;

    @Column(name = "user_role", nullable = false, length = 20)
    private String userRole;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    /**
     * Bank name and last 4 digits of the account this payout was sent to, saved when the payout is requested.
     * Masked, so safe to keep forever: the full account number is archived when the user is purged, and old
     * payouts still show which account was paid.
     */
    @Column(name = "bank_name_snapshot", length = 100)
    private String bankNameSnapshot;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    /** Razorpay Payout id once the real transfer is initiated. */
    @Column(name = "razorpay_payout_id", length = 64)
    private String razorpayPayoutId;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "processed_at")
    private Instant processedAt;
}
