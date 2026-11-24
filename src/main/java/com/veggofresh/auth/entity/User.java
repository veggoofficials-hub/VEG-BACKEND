package com.veggofresh.auth.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Where;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Where(clause = "deleted_at IS NULL")
public class User extends BaseEntity {

    /**
     * Not unique on its own any more: one phone number may own one ACTIVE account per role
     * (unique on (phone, role) - see migration V164).
     */
    @Column(nullable = false, length = 64)
    private String phone;

    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(nullable = false)
    private boolean isVerified = false;

    @Column(nullable = false)
    private boolean isBlocked = false;

    @Column
    private String password;

    // ── Self-service account deletion (migration V175) ──────────────────────

    /**
     * Set alongside {@code deletedAt} when a customer / vendor / delivery partner deletes their OWN
     * account: the end of the reactivation window they chose. {@code null} on every other row,
     * including accounts an admin soft-deleted in the past -- those can never be restored by login.
     * Logging in before this instant restores the account; after it, the 03:00 purge sweep (or the
     * next login) scrubs {@link #phone}/{@link #email} and sets {@link #isPurged}.
     */
    @Column(name = "purge_at")
    private Instant purgeAt;

    /**
     * {@code true} once phone/email have been scrubbed and the password nulled. A purged row is a
     * permanent tombstone: orders and wallet rows keep pointing at it, but it can never be found by
     * phone/email again, so it can never collide with a fresh signup or be reactivated.
     */
    @Column(name = "is_purged", nullable = false)
    private boolean isPurged = false;

    // ── Block details (migration V176). isBlocked above stays the single on/off switch. ──

    /** Why an admin blocked this account; shown to the person. {@code null} while not blocked. */
    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_by_admin_id")
    private UUID blockedByAdminId;

    /** While true (and the account is blocked) the person cannot create NEW payout requests. */
    @Column(name = "payouts_on_hold", nullable = false)
    private boolean payoutsOnHold = false;
}
