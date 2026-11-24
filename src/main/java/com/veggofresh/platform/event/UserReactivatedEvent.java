package com.veggofresh.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by the Auth module ({@code AuthServiceImpl}) when a user who was
 * soft-deleted (self-service account deletion) logs back in <b>within their chosen
 * reactivation window</b>, before that window's {@code purgeAt} has passed.
 *
 * <p>Auth only knows how to undelete the {@code users} row itself -- per the module
 * boundary rule, it cannot reach into Customer/Vendor/Delivery's own profile tables
 * directly. Each of those modules listens for this event and reactivates its own
 * {@code CustomerProfile} / {@code Shop} / {@code DeliveryPartnerProfile} row, filtered
 * on {@link #getRole()} so only the relevant module does any work.
 */
@Getter
public class UserReactivatedEvent extends ApplicationEvent {

    private final UUID userId;

    /** e.g. "CUSTOMER", "VENDOR", "DELIVERY" -- passed as a String so listeners in
     *  other modules stay decoupled from Auth's {@code UserRole} enum. */
    private final String role;

    public UserReactivatedEvent(Object source, UUID userId, String role) {
        super(source);
        this.userId = userId;
        this.role = role;
    }
}
