package com.veggofresh.archive.service.impl;

import com.veggofresh.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs at startup and moves the leftovers of accounts that were purged BEFORE the archive existed (bank
 * accounts, KYC documents, change-request documents) into the archive, so an admin can review them.
 *
 * It is safe to leave on: it only touches users that are already purged and still have leftover rows, so
 * once those are archived it finds nothing. One failing user never stops the others or the application.
 * Switch it off with {@code veggofresh.archive.backfill-on-startup=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veggofresh.archive", name = "backfill-on-startup", havingValue = "true", matchIfMissing = true)
public class ArchiveBackfillRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ArchiveBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Object[]> rows = userRepository.findPurgedUsersWithLeftoverDataIncludingDeleted();
            if (rows.isEmpty()) {
                return;
            }
            log.info("Archive backfill: {} purged user(s) still have data to archive", rows.size());
            int users = 0;
            int items = 0;
            for (Object[] row : rows) {
                UUID userId = (UUID) row[0];
                String role = String.valueOf(row[1]);
                try {
                    items += backfillService.backfillUser(userId, role);
                    users++;
                } catch (Exception e) {
                    log.error("Archive backfill failed for user {} (role {}); skipping it", userId, role, e);
                }
            }
            log.info("Archive backfill done: {} user(s), {} item(s) moved to the archive", users, items);
        } catch (Exception e) {
            log.error("Archive backfill skipped because of an error; the application continues", e);
        }
    }
}
