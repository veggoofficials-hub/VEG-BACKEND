package com.veggofresh.admin.service.impl;

import com.veggofresh.auth.entity.DeletedAccountLog;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * "Has this phone number + role been self-deleted before?" -- used to put a returning-user flag on the
 * admin customer / vendor / delivery screens. The match is on phone AND role, so deleting a customer
 * account never flags the same person's vendor account.
 */
@Component
@RequiredArgsConstructor
public class PreviousDeletionLookup {

    /** previouslyDeleted, how many times, and when the latest deletion happened. */
    public record Info(boolean previouslyDeleted, int count, Instant lastDeletedAt) {
        static Info none() {
            return new Info(false, 0, null);
        }
    }

    private final DeletedAccountLogRepository deletedAccountLogRepository;

    public Info forPhone(String phone, String role) {
        if (phone == null || phone.isBlank()) {
            return Info.none();
        }
        long count = deletedAccountLogRepository.countByPhoneAndRole(phone, role);
        if (count == 0) {
            return Info.none();
        }
        Instant last = deletedAccountLogRepository.findFirstByPhoneAndRoleOrderByCreatedAtDesc(phone, role)
                .map(DeletedAccountLog::getCreatedAt)
                .orElse(null);
        return new Info(true, (int) count, last);
    }
}
