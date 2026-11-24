package com.veggofresh.notification.service.impl;

import com.veggofresh.notification.entity.NotificationRecipientRole;
import com.veggofresh.notification.entity.NotificationType;
import com.veggofresh.notification.service.NotificationService;
import com.veggofresh.notification.util.NotificationJson;
import com.veggofresh.platform.event.UserBlockedEvent;
import com.veggofresh.platform.event.UserUnblockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells a person, in-app (saved first) and by push, that an admin blocked or unblocked their account.
 *
 * <p>Best effort on purpose: a failure to notify must never undo the block / unblock itself, so every
 * call is wrapped. A blocked person keeps their login and their device tokens (only account DELETION
 * removes those), so the notification reaches them even while restricted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountBlockNotificationListener {

    private static final String CONTACT_ADMIN = "Please contact admin through the complaint section.";

    private final NotificationService notificationService;

    @EventListener
    public void onBlocked(UserBlockedEvent event) {
        try {
            NotificationRecipientRole role = NotificationRecipientRole.valueOf(event.getRole());
            StringBuilder body = new StringBuilder("Reason: ").append(event.getReason()).append(". ")
                    .append("You can finish work already in progress, but you can't start new work. ");
            if (event.isPayoutsOnHold()) {
                body.append("New payout requests are on hold. ");
            }
            body.append(CONTACT_ADMIN);

            notificationService.send(event.getUserId(), role, NotificationType.ACCOUNT_BLOCKED,
                    "Your account has been blocked", body.toString(),
                    "{\"reason\":" + NotificationJson.str(event.getReason())
                            + ",\"payoutsOnHold\":" + event.isPayoutsOnHold() + "}");
        } catch (Exception e) {
            log.warn("Could not send the block notification to user {}: {}", event.getUserId(), e.getMessage());
        }
    }

    @EventListener
    public void onUnblocked(UserUnblockedEvent event) {
        try {
            NotificationRecipientRole role = NotificationRecipientRole.valueOf(event.getRole());
            StringBuilder body = new StringBuilder("Your account is active again. ");
            switch (role) {
                case VENDOR -> body.append("Turn your shop back online when you're ready.");
                case DELIVERY -> body.append("Go online again when you're ready.");
                default -> body.append("You can place orders again.");
            }
            if (event.getNote() != null && !event.getNote().isBlank()) {
                body.append(" Note from admin: ").append(event.getNote());
            }

            notificationService.send(event.getUserId(), role, NotificationType.ACCOUNT_UNBLOCKED,
                    "Your account has been unblocked", body.toString(),
                    "{\"note\":" + NotificationJson.str(event.getNote()) + "}");
        } catch (Exception e) {
            log.warn("Could not send the unblock notification to user {}: {}", event.getUserId(), e.getMessage());
        }
    }
}
