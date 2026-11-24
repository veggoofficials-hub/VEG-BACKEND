package com.veggofresh.auth.service.impl;

import com.veggofresh.auth.entity.DeletedAccountLog;
import com.veggofresh.auth.entity.RefreshToken;
import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import com.veggofresh.auth.repository.RefreshTokenRepository;
import com.veggofresh.auth.repository.UserRepository;
import com.veggofresh.auth.service.SessionRevocationService;
import com.veggofresh.auth.service.UserAccountService;
import com.veggofresh.platform.event.UserAccountDeletedEvent;
import com.veggofresh.platform.event.UserAccountPurgedEvent;
import com.veggofresh.platform.event.UserReactivatedEvent;
import com.veggofresh.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the Auth-owned half of self-service account deletion. See {@link UserAccountService} for
 * the cross-module contract, and the {@code *AccountLifecycleListener} classes in Customer / Vendor /
 * Delivery for the other half (restoring or scrubbing their own profile rows when the events fire).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeletedAccountLogRepository deletedAccountLogRepository;
    private final SessionRevocationService sessionRevocationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    // ───────────────────────────── delete (Day 0) ─────────────────────────────

    @Override
    @Transactional
    public void softDeleteAccount(UUID userId, String role, int retentionDays, String reason,
                                  String fullName, Integer totalOrders, BigDecimal totalAmount) {

        if (!ALLOWED_RETENTION_DAYS.contains(retentionDays)) {
            throw new BusinessException("ACCOUNT_DELETE_INVALID_RETENTION",
                    "Retention window must be one of " + ALLOWED_RETENTION_DAYS + " days", HttpStatus.BAD_REQUEST);
        }

        // Native lookup that ignores @Where, so an already-deleted account gets an accurate "already
        // deleted" answer instead of a misleading "not found".
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_DELETE_USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException("ACCOUNT_DELETE_ALREADY_DELETED", "Account is already deleted", HttpStatus.CONFLICT);
        }

        // A blocked account cannot be deleted: otherwise a block could be escaped by deleting the account
        // and signing up again with the same number.
        if (user.isBlocked()) {
            throw new BusinessException("ACCOUNT_DELETE_BLOCKED",
                    "Your account is blocked, so it cannot be deleted right now. "
                            + "Please contact admin through the complaint section.", HttpStatus.FORBIDDEN);
        }

        Instant now = Instant.now();
        Instant purgeAt = retentionDays <= 0 ? now : now.plus(retentionDays, ChronoUnit.DAYS);

        // ── Permanent audit record -- captured BEFORE any scrubbing ──────────────
        // (named auditLog, not "log": @Slf4j already generates a field called log)
        DeletedAccountLog auditLog = new DeletedAccountLog();
        auditLog.setUserId(userId);
        auditLog.setRole(role);
        auditLog.setPhone(user.getPhone());
        auditLog.setEmail(user.getEmail());
        auditLog.setFullName(fullName);
        auditLog.setAccountCreatedAt(user.getCreatedAt());
        auditLog.setRequestedRetentionDays(retentionDays);
        auditLog.setScheduledPurgeAt(purgeAt);
        auditLog.setReason(reason);
        auditLog.setInitiatedBy("SELF");
        auditLog.setTotalOrders(totalOrders);
        auditLog.setTotalAmount(totalAmount);
        deletedAccountLogRepository.save(auditLog);

        // ── Soft-delete the users row ────────────────────────────────────────────
        user.softDelete();
        user.setPurgeAt(purgeAt);
        userRepository.save(user);

        // ── Kill every session ───────────────────────────────────────────────────
        // 1) refresh tokens: no device can obtain a new access token any more;
        // 2) access tokens already issued: refused from now on (logged-out-since list).
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        refreshTokenRepository.deleteAll(tokens);
        sessionRevocationService.revoke(userId, user.getDeletedAt() != null ? user.getDeletedAt() : now);

        eventPublisher.publishEvent(new UserAccountDeletedEvent(this, userId, role, purgeAt));
        log.info("Account soft-deleted: user={}, role={}, retentionDays={}, purgeAt={}", userId, role, retentionDays, purgeAt);

        // "Immediate" -- no window at all, scrub right now.
        if (retentionDays <= 0) {
            purgeAccount(user, role);
        }
    }

    // ───────────────────────────── login: come back ─────────────────────────────

    @Override
    @Transactional
    public Optional<User> reactivateIfWithinWindow(String phone, UserRole role) {
        Optional<User> found = userRepository.findLatestSelfDeletedByPhoneAndRole(phone, role.name());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        Instant now = Instant.now();

        if (user.getPurgeAt() != null && now.isBefore(user.getPurgeAt())) {
            // Inside the window: restore. Old access tokens stay refused (see SessionRevocationServiceImpl);
            // the fresh login that follows carries a later issued-at time and passes.
            user.setDeletedAt(null);
            user.setPurgeAt(null);
            userRepository.save(user);

            deletedAccountLogRepository
                    .findFirstByUserIdAndReactivatedAtIsNullAndPurgedAtIsNullOrderByCreatedAtDesc(user.getId())
                    .ifPresent(entry -> {
                        entry.setReactivatedAt(now);
                        deletedAccountLogRepository.save(entry);
                    });

            eventPublisher.publishEvent(new UserReactivatedEvent(this, user.getId(), role.name()));
            log.info("Account reactivated: user={}, role={}", user.getId(), role);
            return Optional.of(user);
        }

        // Window is over but the 03:00 sweep has not reached it yet: do the same clean-up the sweep would.
        purgeAccount(user, role.name());
        return Optional.empty();
    }

    // ───────────────────────────── purge ─────────────────────────────

    /**
     * Runs once a day. Every self-deleted account whose window has ended is scrubbed, each in its OWN
     * transaction so one failure cannot roll back the others.
     */
    @Scheduled(cron = "0 0 3 * * *") // 03:00 daily -- low-traffic hour
    public void purgeExpiredAccounts() {
        try {
            List<User> expired = userRepository.findExpiredUnpurgedAccounts(Instant.now());
            if (expired.isEmpty()) {
                return;
            }
            log.info("Account purge sweep: {} account(s) past their reactivation window", expired.size());

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            for (User candidate : expired) {
                UUID id = candidate.getId();
                try {
                    tx.executeWithoutResult(status ->
                            userRepository.findByIdIncludingDeleted(id).ifPresent(user -> {
                                if (!user.isPurged()) {
                                    purgeAccount(user, user.getRole().name());
                                }
                            }));
                } catch (Exception e) {
                    log.error("Failed to purge account {}: {}", id, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Account purge sweep error: {}", e.getMessage(), e);
        }
    }

    /**
     * Scrubs phone / e-mail / password of an already soft-deleted user, marks it purged, stamps the audit
     * log and publishes {@code UserAccountPurgedEvent} so Customer / Vendor / Delivery can clean their own
     * tables. The placeholders contain only the user id -- no part of the real phone number or e-mail
     * survives on the users row (the permanent audit log keeps the real values for admin).
     */
    private void purgeAccount(User user, String role) {
        Instant now = Instant.now();
        user.setPhone("deleted_" + user.getId());
        if (user.getEmail() != null) {
            user.setEmail("deleted_" + user.getId() + "@deleted.invalid");
        }
        user.setPassword(null);
        user.setPurged(true);
        userRepository.save(user);

        deletedAccountLogRepository
                .findFirstByUserIdAndReactivatedAtIsNullAndPurgedAtIsNullOrderByCreatedAtDesc(user.getId())
                .ifPresent(entry -> {
                    entry.setPurgedAt(now);
                    deletedAccountLogRepository.save(entry);
                });

        eventPublisher.publishEvent(new UserAccountPurgedEvent(this, user.getId(), role));
        log.info("Account purged (scrubbed): user={}, role={}", user.getId(), role);
    }
}
