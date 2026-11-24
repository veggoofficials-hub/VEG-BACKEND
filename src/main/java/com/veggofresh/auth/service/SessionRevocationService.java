package com.veggofresh.auth.service;

import com.veggofresh.platform.security.SessionRevocationChecker;

import java.time.Instant;
import java.util.UUID;

/**
 * The "logged-out-since" list. Access tokens are stateless JWTs, so the server cannot delete one from
 * the phone that holds it -- but it CAN refuse it. When an account is deleted, {@link #revoke} records
 * {@code userId -> moment}; the JWT filter then refuses every access token of that user that was issued
 * before that moment, on every device, with one in-memory lookup and no database call.
 */
public interface SessionRevocationService extends SessionRevocationChecker {

    /** Refuse every access token of {@code userId} issued before {@code at}. */
    void revoke(UUID userId, Instant at);
}
