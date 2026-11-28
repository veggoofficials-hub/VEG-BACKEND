package com.veggofresh.delivery.service.impl;

import com.veggofresh.delivery.repository.DeliveryPartnerProfileRepository;
import com.veggofresh.platform.event.UserAccountPurgedEvent;
import com.veggofresh.platform.event.UserReactivatedEvent;
import com.veggofresh.platform.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery's half of the account-deletion lifecycle that Auth cannot reach directly (module boundary --
 * see {@code UserAccountService}). Both events are filtered on {@code role == "DELIVERY"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAccountLifecycleListener {

    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final CloudinaryService cloudinaryService;
    private final DeliveryAccountArchiver deliveryAccountArchiver;

    /** Login within the reactivation window -- restore the profile row untouched. */
    @EventListener
    @Transactional
    public void onReactivated(UserReactivatedEvent event) {
        if (!"DELIVERY".equals(event.getRole())) return;

        deliveryPartnerProfileRepository.findByUserIdIncludingDeleted(event.getUserId()).ifPresent(profile -> {
            if (profile.isDeleted()) {
                profile.setDeletedAt(null);
                deliveryPartnerProfileRepository.save(profile);
                log.info("DeliveryPartnerProfile reactivated for user {}", event.getUserId());
            }
        });
    }

    /**
     * Window expired (or "Immediate" was chosen) and the users row has been scrubbed -- point of no
     * return. Scrubs personal / financial identity fields (name, e-mail, avatar, bank details, emergency
     * contact). Vehicle and licence fields stay. Assignments, wallet transactions and ratings are never
     * touched here.
     */
    @EventListener
    @Transactional
    public void onPurged(UserAccountPurgedEvent event) {
        if (!"DELIVERY".equals(event.getRole())) return;
        // Bank details, emergency contact, KYC documents and change-request documents are moved to the
        // archive BEFORE the live fields are cleared below.
        deliveryAccountArchiver.archiveAndRemove(event.getUserId());

        deliveryPartnerProfileRepository.findByUserIdIncludingDeleted(event.getUserId()).ifPresent(profile -> {
            cloudinaryService.deleteQuietly(profile.getAvatarPublicId());
            profile.setFullName(null);
            profile.setEmail(null);
            profile.setAvatarUrl(null);
            profile.setAvatarPublicId(null);
            profile.setBankName(null);
            profile.setAccountHolderName(null);
            profile.setAccountNumber(null);
            profile.setIfscCode(null);
            profile.setEmergencyContactName(null);
            profile.setEmergencyContactRelationship(null);
            profile.setEmergencyContactPhone(null);
            deliveryPartnerProfileRepository.save(profile);
            log.info("Delivery partner personal/financial data scrubbed for user {}", event.getUserId());
        });
    }
}
