package com.veggofresh.vendor.service.impl;

import com.veggofresh.platform.event.UserBlockedEvent;
import com.veggofresh.vendor.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * When an admin blocks a VENDOR the shop is taken offline, so customers stop seeing it and no new orders
 * arrive. Orders the shop already accepted are NOT touched -- they finish normally. The vendor must go
 * online again themselves after an unblock (nothing is switched back on automatically).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VendorAccountBlockListener {

    private final ShopRepository shopRepository;

    @EventListener
    @Transactional
    public void onBlocked(UserBlockedEvent event) {
        if (!"VENDOR".equals(event.getRole())) return;

        shopRepository.findByOwnerUserIdAndDeletedAtIsNull(event.getUserId()).ifPresent(shop -> {
            if (shop.isOnline()) {
                shop.setOnline(false);
                shopRepository.save(shop);
                log.info("Shop {} taken offline because its owner {} was blocked", shop.getId(), event.getUserId());
            }
        });
    }
}
