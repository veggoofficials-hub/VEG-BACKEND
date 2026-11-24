package com.veggofresh.customer.service.impl;

import com.veggofresh.auth.service.UserAccountService;
import com.veggofresh.customer.dto.request.AccountDeletionRequestDto;
import com.veggofresh.customer.entity.CustomerProfile;
import com.veggofresh.customer.entity.OrderStatus;
import com.veggofresh.customer.repository.CustomerProfileRepository;
import com.veggofresh.customer.repository.OrderRepository;
import com.veggofresh.customer.service.CustomerAccountDeletionService;
import com.veggofresh.payment.dto.WalletBalanceDto;
import com.veggofresh.payment.service.WalletService;
import com.veggofresh.platform.exception.BusinessException;
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
public class CustomerAccountDeletionServiceImpl implements CustomerAccountDeletionService {

    /** Anything not DELIVERED / CANCELLED. */
    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);

    private final OrderRepository orderRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final WalletService walletService;
    private final UserAccountService userAccountService;

    @Override
    @Transactional
    public void deleteAccount(UUID userId, AccountDeletionRequestDto request) {
        if (!UserAccountService.ALLOWED_RETENTION_DAYS.contains(request.getRetentionDays())) {
            throw new BusinessException("ACCOUNT_DELETE_INVALID_RETENTION",
                    "Retention window must be one of " + UserAccountService.ALLOWED_RETENTION_DAYS + " days",
                    HttpStatus.BAD_REQUEST);
        }

        // -- Pre-checks ---------------------------------------------------------
        if (orderRepository.existsByUserIdAndStatusIn(userId, ACTIVE_ORDER_STATUSES)) {
            throw new BusinessException("ACCOUNT_DELETE_ACTIVE_ORDER",
                    "You have an order in progress. Please wait for it to complete or be cancelled before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        WalletBalanceDto wallet = walletService.getBalance(userId);
        if (wallet.getBalance() != null && wallet.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("ACCOUNT_DELETE_WALLET_NOT_ZERO",
                    "Please withdraw your wallet balance before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        // -- Stats snapshot for the permanent audit log -------------------------
        CustomerProfile profile = customerProfileRepository.findByUserId(userId).orElse(null);
        String fullName = profile != null ? profile.getFullName() : null;
        Integer totalOrders = (int) orderRepository.countByUserId(userId);
        BigDecimal totalAmount = orderRepository.sumTotalAmountByUserId(userId);

        // -- Hand off to Auth: soft-delete users row, revoke sessions, audit log --
        userAccountService.softDeleteAccount(userId, "CUSTOMER", request.getRetentionDays(),
                request.getReason(), fullName, totalOrders, totalAmount);

        // -- Soft-delete our own profile row. Cart / wishlist / addresses are deliberately left alone
        //    here: they are only hard-deleted once the window really expires (lifecycle listener). --
        if (profile != null) {
            profile.softDelete();
            customerProfileRepository.save(profile);
        }

        log.info("Customer self-deleted account: user={}, retentionDays={}", userId, request.getRetentionDays());
    }
}
