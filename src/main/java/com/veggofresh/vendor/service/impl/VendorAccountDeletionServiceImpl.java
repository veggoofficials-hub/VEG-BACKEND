package com.veggofresh.vendor.service.impl;

import com.veggofresh.auth.service.UserAccountService;
import com.veggofresh.customer.dto.response.OrderResponseDto;
import com.veggofresh.customer.service.CustomerOrderService;
import com.veggofresh.payment.dto.WalletBalanceDto;
import com.veggofresh.payment.entity.PayoutRequestStatus;
import com.veggofresh.payment.repository.PayoutRequestRepository;
import com.veggofresh.payment.service.WalletService;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.vendor.dto.request.AccountDeletionRequestDto;
import com.veggofresh.vendor.entity.Shop;
import com.veggofresh.vendor.repository.ShopRepository;
import com.veggofresh.vendor.repository.VendorListingRepository;
import com.veggofresh.vendor.service.VendorAccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorAccountDeletionServiceImpl implements VendorAccountDeletionService {

    /** Money "out the door but not settled" -- same reasoning as an un-withdrawn wallet balance. */
    private static final List<PayoutRequestStatus> IN_FLIGHT_PAYOUT_STATUSES =
            List.of(PayoutRequestStatus.PENDING, PayoutRequestStatus.APPROVED, PayoutRequestStatus.PROCESSING);

    private final ShopRepository shopRepository;
    private final VendorListingRepository vendorListingRepository;
    private final CustomerOrderService customerOrderService;
    private final WalletService walletService;
    private final PayoutRequestRepository payoutRequestRepository;
    private final UserAccountService userAccountService;

    @Override
    @Transactional
    public void deleteAccount(UUID ownerUserId, AccountDeletionRequestDto request) {
        if (!UserAccountService.ALLOWED_RETENTION_DAYS.contains(request.getRetentionDays())) {
            throw new BusinessException("ACCOUNT_DELETE_INVALID_RETENTION",
                    "Retention window must be one of " + UserAccountService.ALLOWED_RETENTION_DAYS + " days",
                    HttpStatus.BAD_REQUEST);
        }

        Shop shop = shopRepository.findByOwnerUserIdAndDeletedAtIsNull(ownerUserId)
                .orElseThrow(() -> new BusinessException("VENDOR_SHOP_NOT_FOUND", "Shop not found", HttpStatus.NOT_FOUND));

        // -- Pre-checks ---------------------------------------------------------
        if (customerOrderService.hasActiveOrdersForShop(shop.getId())) {
            throw new BusinessException("ACCOUNT_DELETE_ACTIVE_ORDER",
                    "You have an order in progress. Please complete or resolve it before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        WalletBalanceDto wallet = walletService.getBalance(ownerUserId);
        if (wallet.getBalance() != null && wallet.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("ACCOUNT_DELETE_WALLET_NOT_ZERO",
                    "Please withdraw your wallet balance before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        if (payoutRequestRepository.existsByUserIdAndStatusIn(ownerUserId, IN_FLIGHT_PAYOUT_STATUSES)) {
            throw new BusinessException("ACCOUNT_DELETE_PAYOUT_IN_FLIGHT",
                    "You have a payout request still being processed. Please wait for it to complete before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        // -- Stats snapshot for the permanent audit log -------------------------
        List<OrderResponseDto> history = customerOrderService.getAcceptedOrdersForShop(shop.getId());
        Integer totalOrders = history.size();
        BigDecimal totalAmount = history.stream()
                .map(OrderResponseDto::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // -- Hand off to Auth: soft-delete users row, revoke sessions, audit log --
        userAccountService.softDeleteAccount(ownerUserId, "VENDOR", request.getRetentionDays(),
                request.getReason(), shop.getFullName(), totalOrders, totalAmount);

        // -- Soft-delete our own Shop row (filtered by @Where -- see Shop.java). Personal and financial
        //    fields stay untouched here; they are only scrubbed once the window really expires. --
        shop.softDelete();
        shopRepository.save(shop);

        // -- Take the shop's products off the shelf in the same transaction. Listings are NOT soft-deleted
        //    with the shop, so without this they keep pointing at a shop Hibernate can no longer load and
        //    every product page that touches them fails. Flagged so reactivation can relist exactly these. --
        int unlisted = vendorListingRepository.unlistAllForAccountDeletion(shop.getId());
        log.info("Unlisted {} listing(s) of shop {} because its owner deleted the account", unlisted, shop.getId());

        log.info("Vendor self-deleted account: user={}, shop={}, retentionDays={}", ownerUserId, shop.getId(), request.getRetentionDays());
    }
}
