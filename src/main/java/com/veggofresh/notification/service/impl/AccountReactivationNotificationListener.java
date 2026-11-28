package com.veggofresh.notification.service.impl;

import com.veggofresh.notification.entity.NotificationRecipientRole;
import com.veggofresh.notification.entity.NotificationType;
import com.veggofresh.notification.service.NotificationService;
import com.veggofresh.platform.event.UserReactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells a person that their account was restored: a stored (inbox) notification, in addition to the
 * {@code reactivated} flag on the login response. A push will not reach the device (its tokens were removed
 * when the account was deleted), but the inbox entry and unread badge are there when the app loads.
 *
 * IMPORTANT: this runs inside the LOGIN transaction. The notice must never be able to break a login, so it is
 * written in its OWN transaction (REQUIRES_NEW) and every failure is caught here. Catching alone would not be
 * enough: an exception inside the shared transaction would mark the whole login for rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountReactivationNotificationListener {

    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    @EventListener
    public void onReactivated(UserReactivatedEvent event) {
        try {
            NotificationRecipientRole role = NotificationRecipientRole.valueOf(event.getRole());
            TransactionTemplate separateTx = new TransactionTemplate(transactionManager);
            separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            separateTx.executeWithoutResult(status -> notificationService.send(
                    event.getUserId(), role, NotificationType.ACCOUNT_REACTIVATED,
                    "Welcome back!", bodyFor(role), null));
        } catch (Exception e) {
            log.warn("Could not store the 'account reactivated' notification for user {}", event.getUserId(), e);
        }
    }

    private static String bodyFor(NotificationRecipientRole role) {
        switch (role) {
            case VENDOR:
                return "Your account is restored and your products are listed again. Your shop is offline: "
                        + "go online when you're ready for orders.";
            case DELIVERY:
                return "Your account is restored. You're offline: go online when you're ready for deliveries.";
            default:
                return "Your account is restored with your addresses, wishlist and order history.";
        }
    }
}
