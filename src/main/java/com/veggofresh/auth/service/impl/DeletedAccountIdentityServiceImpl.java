package com.veggofresh.auth.service.impl;

import com.veggofresh.auth.dto.DeletedAccountIdentity;
import com.veggofresh.auth.entity.DeletedAccountLog;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import com.veggofresh.auth.service.DeletedAccountIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeletedAccountIdentityServiceImpl implements DeletedAccountIdentityService {

    private final DeletedAccountLogRepository deletedAccountLogRepository;

    @Override
    public Optional<DeletedAccountIdentity> findByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return deletedAccountLogRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).map(this::toIdentity);
    }

    @Override
    public Map<UUID, DeletedAccountIdentity> findByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, DeletedAccountIdentity> result = new HashMap<>();
        // Newest first: the first row seen for a user is its latest deletion.
        for (DeletedAccountLog l : deletedAccountLogRepository.findByUserIdInOrderByCreatedAtDesc(userIds)) {
            result.putIfAbsent(l.getUserId(), toIdentity(l));
        }
        return result;
    }

    private DeletedAccountIdentity toIdentity(DeletedAccountLog l) {
        String status = l.getPurgedAt() != null
                ? DeletedAccountIdentity.STATUS_PURGED
                : DeletedAccountIdentity.STATUS_DELETED;
        return new DeletedAccountIdentity(l.getUserId(), l.getRole(), l.getPhone(), l.getEmail(),
                l.getFullName(), status, l.getCreatedAt(), l.getPurgedAt());
    }
}
