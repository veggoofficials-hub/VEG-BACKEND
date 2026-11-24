package com.veggofresh.vendor.service;

import com.veggofresh.vendor.dto.request.AccountDeletionRequestDto;

import java.util.UUID;

public interface VendorAccountDeletionService {

    /**
     * Deletes the calling vendor's account. Blocks with a {@code BusinessException} if their shop has an
     * order still in flight, a non-zero wallet balance, a payout request still pending / approved /
     * processing, or if the account is blocked. On success, hands off to Auth (soft-deletes {@code users},
     * revokes sessions, writes the audit log) and soft-deletes this vendor's own {@code Shop} row.
     */
    void deleteAccount(UUID ownerUserId, AccountDeletionRequestDto request);
}
