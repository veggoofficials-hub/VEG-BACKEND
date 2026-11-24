package com.veggofresh.notification.service.impl;

import com.veggofresh.notification.repository.UserDeviceTokenRepository;
import com.veggofresh.platform.event.UserAccountDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops push immediately when any account (customer/vendor/delivery) is self-deleted --
 * intentionally NOT deferred to the purge job, unlike cart/wishlist/addresses/profile
 * fields, because there's no reason to keep sending FCM pushes to someone who just
 * deleted their account, even if they could still reactivate within their chosen window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionNotificationListener {

    private final UserDeviceTokenRepository userDeviceTokenRepository;

    @EventListener
    @Transactional
    public void onAccountDeleted(UserAccountDeletedEvent event) {
        var tokens = userDeviceTokenRepository.findByUserId(event.getUserId());
        if (tokens.isEmpty()) return;

        userDeviceTokenRepository.deleteAll(tokens);
        log.info("Cleared {} device token(s) for deleted account {} (role={})", tokens.size(), event.getUserId(), event.getRole());
    }
}
