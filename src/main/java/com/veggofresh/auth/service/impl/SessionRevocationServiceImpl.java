package com.veggofresh.auth.service.impl;

import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.repository.UserRepository;
import com.veggofresh.auth.service.SessionRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "logged-out-since" list.
 *
 * <ul>
 *   <li>{@link #revoke} adds an entry the moment an account is deleted -- instant on this instance.</li>
 *   <li>Every 30 seconds (and once at start-up) the list is topped up from the database with every
 *       account soft-deleted within the last access-token lifetime. That is what makes a restart safe
 *       and lets a second application instance catch up within about 30 seconds.</li>
 *   <li>Entries are never removed because someone logged back in: the person's OLD tokens must stay
 *       refused (their new login simply carries a later issued-at time and passes). An entry expires
 *       on its own once it is older than the longest an access token can live.</li>
 * </ul>
 *
 * <p>The comparison is made in whole seconds because a JWT's {@code iat} has second resolution: a token
 * is refused only if it was issued in an EARLIER second than the revocation, so a login that happens in
 * the very same second as the deletion (a reactivation) is never refused by mistake.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRevocationServiceImpl implements SessionRevocationService {

    private final UserRepository userRepository;

    /** Same property JwtTokenProvider reads; used only to know how long an entry must be kept. */
    @Value("${veggofresh.jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    private final Map<UUID, Instant> revokedAt = new ConcurrentHashMap<>();

    @Override
    public void revoke(UUID userId, Instant at) {
        revokedAt.merge(userId, at, (a, b) -> a.isAfter(b) ? a : b);
    }

    @Override
    public boolean isRevoked(UUID userId, Instant tokenIssuedAt) {
        Instant cutoff = revokedAt.get(userId);
        if (cutoff == null || tokenIssuedAt == null) {
            return false;
        }
        return tokenIssuedAt.getEpochSecond() < cutoff.getEpochSecond();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refreshFromDatabase();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void refreshFromDatabase() {
        try {
            // keep a safety margin of one extra hour beyond the token lifetime
            Duration keep = Duration.ofMillis(accessTokenExpiryMs).plusHours(1);
            Instant now = Instant.now();

            List<User> recentlyDeleted = userRepository.findDeletedSince(now.minus(keep));
            for (User u : recentlyDeleted) {
                if (u.getDeletedAt() != null) {
                    revoke(u.getId(), u.getDeletedAt());
                }
            }

            Instant dropBefore = now.minus(keep);
            revokedAt.values().removeIf(at -> at.isBefore(dropBefore));
        } catch (Exception e) {
            // A failed refresh must never break requests; the next run tries again.
            log.warn("Could not refresh the logged-out-since list: {}", e.getMessage());
        }
    }
}
