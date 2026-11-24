package com.veggofresh.customer.service;

import com.veggofresh.customer.dto.request.AccountDeletionRequestDto;

import java.util.UUID;

public interface CustomerAccountDeletionService {

    /**
     * Deletes the calling customer's account. Blocks with a {@code BusinessException} if they have any
     * order still in flight, a non-zero wallet balance, or are blocked. On success, hands off to Auth
     * (soft-deletes {@code users}, revokes sessions, writes the audit log) and soft-deletes this
     * customer's own {@code CustomerProfile} row.
     */
    void deleteAccount(UUID userId, AccountDeletionRequestDto request);
}
