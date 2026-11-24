package com.veggofresh.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by the Auth module once a soft-deleted account's reactivation window has
 * fully expired (or immediately, if the user chose "delete immediately") AND the
 * {@code users} row's phone/email have been scrubbed. This is the point of no return --
 * unlike {@link UserAccountDeletedEvent} (Day 0, still undoable), a purge is final.
 *
 * <p>Listeners (Customer/Vendor/Delivery, filtered on {@link #getRole()}) now do the
 * data cleanup that was deliberately deferred past Day 0 so a reactivation within the
 * window would restore everything untouched:
 * <ul>
 *   <li>Customer: hard-deletes carts, wishlist, addresses; scrubs {@code CustomerProfile}
 *       (name/email/avatar).</li>
 *   <li>Vendor: scrubs {@code Shop}'s personal/financial fields (owner name, email,
 *       phone, bank details, profile photo). Store name/photo are left untouched since
 *       they still appear on the vendor's historical orders.</li>
 *   <li>Delivery: scrubs {@code DeliveryPartnerProfile}'s personal/financial fields
 *       (name, email, avatar, bank details, emergency contact).</li>
 * </ul>
 * Orders, payments, wallet transactions, payout requests, and ratings are never touched
 * by any listener -- they are financial/audit records, not identity data.
 */
@Getter
public class UserAccountPurgedEvent extends ApplicationEvent {

    private final UUID userId;
    private final String role;

    public UserAccountPurgedEvent(Object source, UUID userId, String role) {
        super(source);
        this.userId = userId;
        this.role = role;
    }
}
