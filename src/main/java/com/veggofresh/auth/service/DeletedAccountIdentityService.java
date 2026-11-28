package com.veggofresh.auth.service;

import com.veggofresh.auth.dto.DeletedAccountIdentity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One place to ask "who was this user?" once the live user/profile can no longer be found.
 * Screens look the person up live first and fall back to this, so history never shows blank details.
 */
public interface DeletedAccountIdentityService {

    /** Identity of a deleted/purged account, or empty when the user never deleted their account. */
    Optional<DeletedAccountIdentity> findByUserId(UUID userId);

    /** Same, for many users at once (one query). Users without a deletion record are simply absent. */
    Map<UUID, DeletedAccountIdentity> findByUserIds(Collection<UUID> userIds);
}
