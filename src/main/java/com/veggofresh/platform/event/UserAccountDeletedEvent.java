package com.veggofresh.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by the Auth module the moment a self-delete request is confirmed (Day 0)
 * -- i.e. right after the {@code users} row is soft-deleted and its refresh tokens are
 * revoked. Signals every other module that this user's session must die immediately,
 * regardless of which reactivation window they chose.
 *
 * <p>Today the only listener is the Notification module, which deletes the user's
 * {@code UserDeviceToken} rows so FCM push stops instantly instead of waiting for the
 * eventual purge. Other modules do NOT need to react to this event for their own
 * profile-row soft-delete -- that happens synchronously, in the same request, inside
 * each role's own account-deletion service (Customer/Vendor/Delivery), since it's
 * already local to that module.
 */
@Getter
public class UserAccountDeletedEvent extends ApplicationEvent {

    private final UUID userId;
    private final String role;
    private final Instant purgeAt;

    public UserAccountDeletedEvent(Object source, UUID userId, String role, Instant purgeAt) {
        super(source);
        this.userId = userId;
        this.role = role;
        this.purgeAt = purgeAt;
    }
}
