package com.veggofresh.platform.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by the Auth module when an admin BLOCKS an account (Customer / Vendor / Delivery).
 *
 * <p>A block is "restricted mode": the person keeps their login and can finish work already in
 * progress, but cannot start new work. Auth only owns the {@code users} row, so the side effects
 * that belong to other modules are done by listeners (filtered on {@link #getRole()}):
 * <ul>
 *   <li>Vendor: the shop is taken offline so customers stop seeing it.</li>
 *   <li>Delivery: the partner is taken offline so no new offers are sent.</li>
 *   <li>Notification: the person is told they were blocked and why.</li>
 * </ul>
 */
@Getter
public class UserBlockedEvent extends ApplicationEvent {

    private final UUID userId;
    /** "CUSTOMER" / "VENDOR" / "DELIVERY" -- a String so listeners stay decoupled from Auth's UserRole. */
    private final String role;
    private final String reason;
    private final boolean payoutsOnHold;

    public UserBlockedEvent(Object source, UUID userId, String role, String reason, boolean payoutsOnHold) {
        super(source);
        this.userId = userId;
        this.role = role;
        this.reason = reason;
        this.payoutsOnHold = payoutsOnHold;
    }
}
