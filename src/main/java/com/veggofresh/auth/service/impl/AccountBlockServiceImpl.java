package com.veggofresh.auth.service.impl;

import com.veggofresh.auth.dto.response.AccountBlockLogResponseDto;
import com.veggofresh.auth.entity.AccountBlockLog;
import com.veggofresh.auth.entity.BlockAction;
import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.auth.repository.AccountBlockLogRepository;
import com.veggofresh.auth.repository.UserRepository;
import com.veggofresh.auth.service.AccountBlockService;
import com.veggofresh.platform.event.UserBlockedEvent;
import com.veggofresh.platform.event.UserUnblockedEvent;
import com.veggofresh.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBlockServiceImpl implements AccountBlockService {

    private static final Set<String> ACTIONS = Set.of("BLOCK", "UNBLOCK");

    private final UserRepository userRepository;
    private final AccountBlockLogRepository accountBlockLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void block(UUID userId, UserRole expectedRole, String reason, boolean holdPayouts,
                      String fullName, UUID adminId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("BLOCK_REASON_REQUIRED", "A reason is required to block an account",
                    HttpStatus.BAD_REQUEST);
        }
        String cleanReason = reason.trim();
        User user = loadOfRole(userId, expectedRole);

        if (user.isBlocked()) {
            throw new BusinessException("ACCOUNT_ALREADY_BLOCKED", "This account is already blocked", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        user.setBlocked(true);
        user.setBlockedReason(cleanReason);
        user.setBlockedAt(now);
        user.setBlockedByAdminId(adminId);
        user.setPayoutsOnHold(holdPayouts);
        userRepository.save(user);

        accountBlockLogRepository.save(logRow(user, BlockAction.BLOCK, cleanReason, holdPayouts, fullName, adminId));
        eventPublisher.publishEvent(new UserBlockedEvent(this, userId, user.getRole().name(), cleanReason, holdPayouts));
        log.info("Account blocked: user={}, role={}, by admin={}, payoutsOnHold={}", userId, user.getRole(), adminId, holdPayouts);
    }

    @Override
    @Transactional
    public void unblock(UUID userId, UserRole expectedRole, String note, String fullName, UUID adminId) {
        User user = loadOfRole(userId, expectedRole);

        if (!user.isBlocked()) {
            throw new BusinessException("ACCOUNT_NOT_BLOCKED", "This account is not blocked", HttpStatus.CONFLICT);
        }
        String cleanNote = (note == null || note.isBlank()) ? null : note.trim();

        user.setBlocked(false);
        user.setBlockedReason(null);
        user.setBlockedAt(null);
        user.setBlockedByAdminId(null);
        user.setPayoutsOnHold(false);
        userRepository.save(user);

        accountBlockLogRepository.save(logRow(user, BlockAction.UNBLOCK, cleanNote, false, fullName, adminId));
        eventPublisher.publishEvent(new UserUnblockedEvent(this, userId, user.getRole().name(), cleanNote));
        log.info("Account unblocked: user={}, role={}, by admin={}", userId, user.getRole(), adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountBlockLogResponseDto> history(String role, String action, String search,
                                                    boolean currentOnly, Pageable pageable) {
        String roleFilter = blankToNull(role);
        if (roleFilter != null) {
            roleFilter = roleFilter.toUpperCase();
        }
        String actionFilter = blankToNull(action);
        if (actionFilter != null) {
            actionFilter = actionFilter.toUpperCase();
            if (!ACTIONS.contains(actionFilter)) {
                throw new BusinessException("INVALID_ACTION_FILTER", "action must be BLOCK or UNBLOCK", HttpStatus.BAD_REQUEST);
            }
        }
        return accountBlockLogRepository
                .search(roleFilter, actionFilter, blankToNull(search), currentOnly, pageable)
                .map(this::toDto);
    }

    // ───────────────────────── helpers ─────────────────────────

    private User loadOfRole(UUID userId, UserRole expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (user.getRole() != expectedRole) {
            throw new BusinessException("USER_ROLE_MISMATCH",
                    "This user is not a " + expectedRole.name().toLowerCase(), HttpStatus.BAD_REQUEST);
        }
        return user;
    }

    private AccountBlockLog logRow(User user, BlockAction action, String reason, boolean payoutsHeld,
                                   String fullName, UUID adminId) {
        AccountBlockLog row = new AccountBlockLog();
        row.setUserId(user.getId());
        row.setRole(user.getRole().name());
        row.setAction(action);
        row.setReason(reason);
        row.setPayoutsHeld(payoutsHeld);
        row.setAdminId(adminId);
        row.setAdminLabel(adminLabel(adminId));
        row.setPhone(user.getPhone());
        row.setFullName(fullName);
        return row;
    }

    private String adminLabel(UUID adminId) {
        if (adminId == null) {
            return null;
        }
        return userRepository.findById(adminId)
                .map(a -> a.getEmail() != null && !a.getEmail().isBlank() ? a.getEmail() : adminId.toString())
                .orElse(adminId.toString());
    }

    private AccountBlockLogResponseDto toDto(AccountBlockLog l) {
        return AccountBlockLogResponseDto.builder()
                .id(l.getId())
                .userId(l.getUserId())
                .role(l.getRole())
                .action(l.getAction().name())
                .reason(l.getReason())
                .payoutsHeld(l.isPayoutsHeld())
                .adminId(l.getAdminId())
                .adminLabel(l.getAdminLabel())
                .phone(l.getPhone())
                .fullName(l.getFullName())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
