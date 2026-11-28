package com.veggofresh.payment.service.impl;

import com.veggofresh.platform.event.UserAccountPurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** When ANY account is purged, its saved bank account is archived and removed from the live table. */
@Component
@RequiredArgsConstructor
public class PaymentAccountLifecycleListener {

    private final PaymentAccountArchiver paymentAccountArchiver;

    @EventListener
    @Transactional
    public void onPurged(UserAccountPurgedEvent event) {
        paymentAccountArchiver.archiveAndRemove(event.getUserId(), event.getRole());
    }
}
