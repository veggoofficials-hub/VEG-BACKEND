package com.veggofresh.admin.service.impl;

import com.veggofresh.admin.dto.response.DeletedAccountResponseDto;
import com.veggofresh.admin.service.AdminDeletedAccountService;
import com.veggofresh.auth.entity.DeletedAccountLog;
import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import com.veggofresh.auth.repository.UserRepository;
import com.veggofresh.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDeletedAccountServiceImpl implements AdminDeletedAccountService {

    private static final Set<String> STATUSES = Set.of("IN_WINDOW", "REACTIVATED", "PURGED", "RETURNED_AS_NEW");
    private static final Set<String> ROLES = Set.of("CUSTOMER", "VENDOR", "DELIVERY");

    private final DeletedAccountLogRepository deletedAccountLogRepository;
    private final UserRepository userRepository;

    @Override
    public Page<DeletedAccountResponseDto> list(String role, String search, String status, Pageable pageable) {
        String roleFilter = blankToNull(role);
        if (roleFilter != null) {
            roleFilter = roleFilter.toUpperCase();
            if (!ROLES.contains(roleFilter)) {
                throw new BusinessException("INVALID_ROLE_FILTER", "role must be CUSTOMER, VENDOR or DELIVERY", HttpStatus.BAD_REQUEST);
            }
        }
        String statusFilter = blankToNull(status);
        if (statusFilter != null) {
            statusFilter = statusFilter.toUpperCase();
            if (!STATUSES.contains(statusFilter)) {
                throw new BusinessException("INVALID_STATUS_FILTER",
                        "status must be one of " + STATUSES, HttpStatus.BAD_REQUEST);
            }
        }
        return deletedAccountLogRepository
                .search(roleFilter, blankToNull(search), statusFilter, pageable)
                .map(this::toDto);
    }

    @Override
    public DeletedAccountResponseDto getById(UUID id) {
        DeletedAccountLog entry = deletedAccountLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("DELETED_ACCOUNT_NOT_FOUND",
                        "Deleted account record not found", HttpStatus.NOT_FOUND));
        return toDto(entry);
    }

    private DeletedAccountResponseDto toDto(DeletedAccountLog l) {
        String status;
        UUID newUserId = null;
        java.time.Instant returnedAt = null;

        if (l.getReactivatedAt() != null) {
            status = "REACTIVATED";
        } else if (l.getPurgedAt() == null) {
            status = "IN_WINDOW";
        } else {
            // Purged. Did the same phone + role come back as a brand-new account?
            Optional<User> current = findActiveAccount(l);
            if (current.isPresent()) {
                status = "RETURNED_AS_NEW";
                newUserId = current.get().getId();
                returnedAt = current.get().getCreatedAt();
            } else {
                status = "PURGED";
            }
        }

        return DeletedAccountResponseDto.builder()
                .id(l.getId())
                .userId(l.getUserId())
                .role(l.getRole())
                .phone(l.getPhone())
                .email(l.getEmail())
                .fullName(l.getFullName())
                .accountCreatedAt(l.getAccountCreatedAt())
                .deletedAt(l.getCreatedAt())
                .requestedRetentionDays(l.getRequestedRetentionDays())
                .scheduledPurgeAt(l.getScheduledPurgeAt())
                .reason(l.getReason())
                .initiatedBy(l.getInitiatedBy())
                .totalOrders(l.getTotalOrders())
                .totalAmount(l.getTotalAmount())
                .status(status)
                .reactivatedAt(l.getReactivatedAt())
                .purgedAt(l.getPurgedAt())
                .returned("RETURNED_AS_NEW".equals(status))
                .newUserId(newUserId)
                .returnedAt(returnedAt)
                .previousDeletions((int) deletedAccountLogRepository.countByPhoneAndRole(l.getPhone(), l.getRole()))
                .build();
    }

    private Optional<User> findActiveAccount(DeletedAccountLog l) {
        try {
            return userRepository.findByPhoneAndRole(l.getPhone(), UserRole.valueOf(l.getRole()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
