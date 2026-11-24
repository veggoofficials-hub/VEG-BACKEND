package com.veggofresh.auth.service;

import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.entity.UserRole;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Public interface for the account-deletion lifecycle actions that only Auth is allowed to perform,
 * because only Auth owns the {@code users} table and {@code RefreshToken}. Other modules must use this
 * instead of importing {@code User}/{@code RefreshToken} directly -- same rule, same pattern as
 * {@link UserLookupService}.
 *
 * <p>Each role module (Customer/Vendor/Delivery) calls {@link #softDeleteAccount} from its own
 * account-deletion service, AFTER it has already run its own pre-delete checks (active orders /
 * assignments, wallet balance, pending payouts) and soft-deleted its own profile row
 * (CustomerProfile / Shop / DeliveryPartnerProfile) -- this call only handles the {@code users} row,
 * the session clean-up and the permanent audit record.
 */
public interface UserAccountService {

    /**
     * Allowed reactivation windows a user may choose at delete time, in days.
     * {@code 0} means "delete immediately" -- no reactivation window at all; the account is scrubbed
     * synchronously as part of the same call.
     */
    java.util.Set<Integer> ALLOWED_RETENTION_DAYS = java.util.Set.of(0, 1, 7, 15, 30);

    /**
     * Soft-deletes the {@code users} row, deletes every refresh token the user holds (logs them out of
     * every device), refuses their still-valid access tokens from now on (logged-out-since list), writes
     * the permanent {@code DeletedAccountLog} record and publishes {@code UserAccountDeletedEvent}.
     * If {@code retentionDays == 0} the account is also scrubbed before this method returns.
     *
     * @param userId        the account being deleted
     * @param role          "CUSTOMER" / "VENDOR" / "DELIVERY" -- carried on the audit log and the events
     * @param retentionDays one of {@link #ALLOWED_RETENTION_DAYS}
     * @param reason        optional free-text reason the user gave, or {@code null}
     * @param fullName      the role module's own display name for this user, for the audit log
     * @param totalOrders   role-specific stat snapshot for the audit log, or {@code null}
     * @param totalAmount   role-specific stat snapshot (spent for a customer, earned for a vendor /
     *                      delivery partner) for the audit log, or {@code null}
     * @throws com.veggofresh.platform.exception.BusinessException if the user does not exist, is already
     *         deleted, is BLOCKED, or {@code retentionDays} is not an allowed value
     */
    void softDeleteAccount(UUID userId, String role, int retentionDays, String reason,
                           String fullName, Integer totalOrders, BigDecimal totalAmount);

    /**
     * Login-time lookup for a returning person. Looks for the most recent SELF-deleted, not yet scrubbed
     * account of this phone number in this role:
     * <ul>
     *   <li>still inside its reactivation window -> the account is restored (users row undeleted,
     *       {@code reactivated_at} written on the audit log, {@code UserReactivatedEvent} published so the
     *       role module restores its own profile / shop) and returned;</li>
     *   <li>window already over but the 03:00 sweep has not run yet -> the account is scrubbed now (same
     *       clean-up as the sweep) and nothing is returned, so the caller creates a brand-new account;</li>
     *   <li>nothing found, or the old account was deleted by an admin -> nothing is returned.</li>
     * </ul>
     */
    Optional<User> reactivateIfWithinWindow(String phone, UserRole role);
}
