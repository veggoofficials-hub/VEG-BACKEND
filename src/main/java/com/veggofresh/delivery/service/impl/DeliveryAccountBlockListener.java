package com.veggofresh.delivery.service.impl;

import com.veggofresh.delivery.repository.DeliveryOnlineSessionRepository;
import com.veggofresh.delivery.repository.DeliveryPartnerProfileRepository;
import com.veggofresh.platform.event.UserBlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * When an admin blocks a DELIVERY partner they are taken offline (and their open online session is
 * closed, exactly as a normal "go offline" does), so no new delivery offers reach them. A delivery they
 * already accepted is NOT touched -- they finish it. They must go online again themselves after an
 * unblock (nothing is switched back on automatically).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAccountBlockListener {

    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final DeliveryOnlineSessionRepository sessionRepository;

    @EventListener
    @Transactional
    public void onBlocked(UserBlockedEvent event) {
        if (!"DELIVERY".equals(event.getRole())) return;

        deliveryPartnerProfileRepository.findByUserId(event.getUserId()).ifPresent(profile -> {
            if (!profile.isOnline()) return;

            profile.setOnline(false);
            deliveryPartnerProfileRepository.save(profile);
            sessionRepository.findByDeliveryPartnerUserIdAndEndedAtIsNull(event.getUserId())
                    .ifPresent(session -> {
                        session.setEndedAt(Instant.now());
                        sessionRepository.save(session);
                    });
            log.info("Delivery partner {} taken offline because the account was blocked", event.getUserId());
        });
    }
}
