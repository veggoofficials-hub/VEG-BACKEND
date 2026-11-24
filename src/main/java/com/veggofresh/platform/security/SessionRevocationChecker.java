package com.veggofresh.platform.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Port used by {@link JwtAuthenticationFilter} to ask "has this user's login been cancelled since this
 * access token was issued?". The Auth module implements it (it owns the users table), so the platform
 * layer does not depend on any module.
 */
public interface SessionRevocationChecker {

    /**
     * @param userId        the token's subject
     * @param tokenIssuedAt the token's {@code iat}
     * @return {@code true} if every token issued to {@code userId} before the cancellation (e.g. the
     *         account was deleted) must be refused
     */
    boolean isRevoked(UUID userId, Instant tokenIssuedAt);
}
