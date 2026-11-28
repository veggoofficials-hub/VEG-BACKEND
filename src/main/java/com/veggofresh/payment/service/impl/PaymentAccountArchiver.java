package com.veggofresh.payment.service.impl;

import com.veggofresh.archive.entity.ArchiveItemType;
import com.veggofresh.archive.service.ArchivePayload;
import com.veggofresh.archive.service.PurgedAccountArchiveService;
import com.veggofresh.payment.entity.UserBankAccount;
import com.veggofresh.payment.repository.UserBankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Moves a purged user's saved bank account (any role) into the archive and removes it from the live
 * table. The payout history keeps the bank name and last 4 digits on each payout row (V180), so old
 * payouts still show which account was paid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAccountArchiver {

    private final UserBankAccountRepository userBankAccountRepository;
    private final PurgedAccountArchiveService archiveService;

    /** @return how many bank accounts were archived and removed (0 or 1). Safe to call repeatedly. */
    @Transactional
    public int archiveAndRemove(UUID userId, String role) {
        Optional<UserBankAccount> found = userBankAccountRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return 0;
        }
        UserBankAccount b = found.get();
        archiveService.archiveItem(userId, role, ArchiveItemType.BANK_ACCOUNT, b.getId(),
                ArchivePayload.of(
                        "accountHolderName", b.getAccountHolderName(),
                        "accountNumber", b.getAccountNumber(),
                        "ifscCode", b.getIfscCode(),
                        "bankName", b.getBankName(),
                        "upiId", b.getUpiId(),
                        "razorpayContactId", b.getRazorpayContactId(),
                        "razorpayFundAccountId", b.getRazorpayFundAccountId()),
                null, null);
        userBankAccountRepository.delete(b);
        log.info("Bank account of purged user {} moved to the archive", userId);
        return 1;
    }
}
