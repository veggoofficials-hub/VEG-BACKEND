package com.veggofresh.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by the Auth module when an admin UNBLOCKS an account. Today the only listener is the
 * Notification module, which tells the person they can work again (with the admin's optional note).
 * A vendor or delivery partner is NOT put back online automatically -- they go online themselves.
 */
@Getter
public class UserUnblockedEvent extends ApplicationEvent {

    private final UUID userId;
    private final String role;
    /** Optional admin note, may be {@code null}. */
    private final String note;

    public UserUnblockedEvent(Object source, UUID userId, String role, String note) {
        super(source);
        this.userId = userId;
        this.role = role;
        this.note = note;
    }
}
