package com.veggofresh.vendor.service.impl;

import com.veggofresh.platform.event.UserAccountPurgedEvent;
import com.veggofresh.platform.event.UserReactivatedEvent;
import com.veggofresh.platform.storage.CloudinaryService;
import com.veggofresh.vendor.repository.ShopRepository;
import com.veggofresh.vendor.repository.VendorListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor's half of the account-deletion lifecycle that Auth cannot reach directly (module boundary --
 * see {@code UserAccountService}). Both events are filtered on {@code role == "VENDOR"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VendorAccountLifecycleListener {

    private final ShopRepository shopRepository;
    private final VendorListingRepository vendorListingRepository;
    private final CloudinaryService cloudinaryService;
    private final VendorAccountArchiver vendorAccountArchiver;

    /** Login within the reactivation window -- restore the shop row untouched. */
    @EventListener
    @Transactional
    public void onReactivated(UserReactivatedEvent event) {
        if (!"VENDOR".equals(event.getRole())) return;

        shopRepository.findByOwnerUserIdIncludingDeleted(event.getUserId()).ifPresent(shop -> {
            if (shop.isDeleted()) {
                shop.setDeletedAt(null);
                shopRepository.save(shop);
                // Bring back the products that were switched off by the account deletion (and only those).
                int relisted = vendorListingRepository.relistAfterReactivation(shop.getId());
                log.info("Shop reactivated for owner {}; relisted {} listing(s)", event.getUserId(), relisted);
            }
        });
    }

    /**
     * Window expired (or "Immediate" was chosen) and the users row has been scrubbed -- point of no
     * return. Scrubs only the owner's PERSONAL / financial identity fields (name, e-mail, phone, bank
     * details, personal photo). The store's own name, photo, bio and address stay: old orders keep their
     * own snapshot of them anyway. Orders and payouts are never touched here.
     */
    @EventListener
    @Transactional
    public void onPurged(UserAccountPurgedEvent event) {
        if (!"VENDOR".equals(event.getRole())) return;
        // Bank details and KYC documents are moved to the archive BEFORE the live fields are cleared below.
        vendorAccountArchiver.archiveAndRemove(event.getUserId());

        shopRepository.findByOwnerUserIdIncludingDeleted(event.getUserId()).ifPresent(shop -> {
            cloudinaryService.deleteQuietly(shop.getProfileImagePublicId());
            shop.setFullName(null);
            shop.setEmail(null);
            shop.setBusinessPhone(null);
            shop.setProfileImageUrl(null);
            shop.setProfileImagePublicId(null);
            shop.setBankName(null);
            shop.setAccountHolderName(null);
            shop.setAccountNumber(null);
            shop.setIfscCode(null);
            shopRepository.save(shop);
            log.info("Vendor personal/financial data scrubbed for owner {} (shop {})", event.getUserId(), shop.getId());
        });
    }
}
