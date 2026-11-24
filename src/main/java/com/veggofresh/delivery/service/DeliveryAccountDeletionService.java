package com.veggofresh.delivery.service;

import com.veggofresh.delivery.dto.request.AccountDeletionRequestDto;

import java.util.UUID;

public interface DeliveryAccountDeletionService {

    /**
     * Deletes the calling delivery partner's account. Blocks with a {@code BusinessException} if they have
     * an assignment still in flight, a non-zero wallet balance, a payout request still pending / approved /
     * processing, or if the account is blocked. On success, hands off to Auth (soft-deletes {@code users},
     * revokes sessions, writes the audit log) and soft-deletes this partner's own
     * {@code DeliveryPartnerProfile} row.
     */
    void deleteAccount(UUID userId, AccountDeletionRequestDto request);
}
