package com.veggofresh.auth.service;

import java.util.UUID;

/**
 * The "new work" doors of restricted mode.
 *
 * <p>A blocked account is NOT locked out: it can log in, see its orders, finish work already in
 * progress and (unless an admin put payouts on hold) withdraw earnings. What it cannot do is START new
 * work. The handful of places that start new work call this service, which reads the live block flag on
 * every call -- so a block bites instantly, even for a person holding an old access token.
 *
 * <p>Current doors: customer checkout (online and COD), a vendor accepting an order, a delivery partner
 * accepting a delivery, a vendor / delivery partner going online, and requesting a payout (hold only).
 */
public interface AccountAccessService {

    /**
     * @param userId the acting account
     * @param action what the person tried to do, as a phrase that completes "you can't ..." --
     *               e.g. {@code "place new orders"}
     * @throws com.veggofresh.platform.exception.BusinessException 403 {@code ACCOUNT_BLOCKED} (message
     *         carries the admin's reason) if the account is blocked
     */
    void requireNotBlocked(UUID userId, String action);

    /**
     * @throws com.veggofresh.platform.exception.BusinessException 403 {@code PAYOUTS_ON_HOLD} if the
     *         account is blocked AND an admin ticked "hold payouts" when blocking it
     */
    void requirePayoutsNotOnHold(UUID userId);
}
