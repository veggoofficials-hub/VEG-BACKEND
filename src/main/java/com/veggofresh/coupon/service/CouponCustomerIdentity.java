package com.veggofresh.coupon.service;

import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import com.veggofresh.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who counts as "the same customer" for coupon rules.
 *
 * A purged account is gone for good, and the next login with the same phone creates a brand-new account (a new
 * user id). Counting orders and coupon uses per user id alone would let a customer delete their account and
 * come back to use "new customer" offers and per-customer limits again. So eligibility is counted across ALL
 * customer accounts the same phone number has ever had, found through the permanent deletion log.
 *
 * (A person who signs up with a DIFFERENT phone number cannot be detected.)
 */
@Component
@RequiredArgsConstructor
class CouponCustomerIdentity {

    /** The answer only changes when someone deletes an account, so a short cache is safe and saves queries. */
    private static final long TTL_MILLIS = 30_000L;
    private static final int MAX_CACHED = 5_000;

    private final UserRepository userRepository;
    private final DeletedAccountLogRepository deletedAccountLogRepository;

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<UUID> ids, long expiresAt) {
    }

    /** The user's own id plus the ids of every earlier customer account of the same phone. Never empty. */
    List<UUID> accountIds(UUID userId) {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(userId);
        if (hit != null && hit.expiresAt() > now) {
            return hit.ids();
        }
        List<UUID> ids = load(userId);
        if (cache.size() >= MAX_CACHED) {
            cache.clear();
        }
        cache.put(userId, new Cached(ids, now + TTL_MILLIS));
        return ids;
    }

    private List<UUID> load(UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(userId);
        userRepository.findById(userId).ifPresent(user ->
                deletedAccountLogRepository.findByPhoneAndRole(user.getPhone(), UserRole.CUSTOMER.name())
                        .forEach(entry -> ids.add(entry.getUserId())));
        return List.copyOf(ids);
    }
}
