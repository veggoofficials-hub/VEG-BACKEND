package com.veggofresh.customer.service.impl;

import com.veggofresh.customer.repository.AddressRepository;
import com.veggofresh.customer.repository.CartRepository;
import com.veggofresh.customer.repository.CustomerProfileRepository;
import com.veggofresh.customer.repository.WishlistRepository;
import com.veggofresh.platform.event.UserAccountPurgedEvent;
import com.veggofresh.platform.event.UserReactivatedEvent;
import com.veggofresh.platform.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer's half of the account-deletion lifecycle that Auth cannot reach directly (module boundary --
 * see {@code UserAccountService}). Both events are filtered on {@code role == "CUSTOMER"} so this is a
 * no-op for vendor / delivery accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerAccountLifecycleListener {

    private final CustomerProfileRepository customerProfileRepository;
    private final CartRepository cartRepository;
    private final WishlistRepository wishlistRepository;
    private final AddressRepository addressRepository;
    private final CloudinaryService cloudinaryService;

    /** Login within the reactivation window -- restore the profile row untouched. */
    @EventListener
    @Transactional
    public void onReactivated(UserReactivatedEvent event) {
        if (!"CUSTOMER".equals(event.getRole())) return;

        customerProfileRepository.findByUserIdIncludingDeleted(event.getUserId()).ifPresent(profile -> {
            if (profile.isDeleted()) {
                profile.setDeletedAt(null);
                customerProfileRepository.save(profile);
                log.info("CustomerProfile reactivated for user {}", event.getUserId());
            }
        });
    }

    /**
     * Window expired (or "Immediate" was chosen) and the users row has been scrubbed -- the point of no
     * return. Hard-delete cart / wishlist / addresses and scrub the profile's own identity fields.
     * Orders, payments and ratings are never touched here (they keep their own snapshot of the details).
     */
    @EventListener
    @Transactional
    public void onPurged(UserAccountPurgedEvent event) {
        if (!"CUSTOMER".equals(event.getRole())) return;

        cartRepository.findByUserIdOrderByCreatedAtAscIdAsc(event.getUserId())
                .forEach(cartRepository::delete); // cascades to CartItem (orphanRemoval)
        wishlistRepository.findByUserId(event.getUserId())
                .forEach(wishlistRepository::delete);
        addressRepository.findByUserId(event.getUserId())
                .forEach(addressRepository::delete);

        customerProfileRepository.findByUserIdIncludingDeleted(event.getUserId()).ifPresent(profile -> {
            cloudinaryService.deleteQuietly(profile.getAvatarPublicId());
            profile.setFullName(null);
            profile.setEmail(null);
            profile.setAvatarUrl(null);
            profile.setAvatarPublicId(null);
            customerProfileRepository.save(profile);
        });

        log.info("Customer data purged for user {} (cart/wishlist/addresses hard-deleted, profile scrubbed)", event.getUserId());
    }
}
