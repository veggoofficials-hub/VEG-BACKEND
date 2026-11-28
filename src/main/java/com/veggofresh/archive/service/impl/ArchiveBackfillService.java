package com.veggofresh.archive.service.impl;

import com.veggofresh.delivery.service.impl.DeliveryAccountArchiver;
import com.veggofresh.payment.service.impl.PaymentAccountArchiver;
import com.veggofresh.vendor.service.impl.VendorAccountArchiver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Archives the leftovers of ONE already-purged user, in its own transaction. */
@Service
@RequiredArgsConstructor
public class ArchiveBackfillService {

    private final PaymentAccountArchiver paymentAccountArchiver;
    private final VendorAccountArchiver vendorAccountArchiver;
    private final DeliveryAccountArchiver deliveryAccountArchiver;

    @Transactional
    public int backfillUser(UUID userId, String role) {
        int n = paymentAccountArchiver.archiveAndRemove(userId, role);
        if ("VENDOR".equals(role)) {
            n += vendorAccountArchiver.archiveAndRemove(userId);
        } else if ("DELIVERY".equals(role)) {
            n += deliveryAccountArchiver.archiveAndRemove(userId);
        }
        return n;
    }
}
