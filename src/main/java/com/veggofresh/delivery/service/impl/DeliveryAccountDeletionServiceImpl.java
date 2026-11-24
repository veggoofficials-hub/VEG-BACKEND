package com.veggofresh.delivery.service.impl;

import com.veggofresh.auth.service.UserAccountService;
import com.veggofresh.delivery.dto.request.AccountDeletionRequestDto;
import com.veggofresh.delivery.entity.DeliveryAssignmentStatus;
import com.veggofresh.delivery.entity.DeliveryPartnerProfile;
import com.veggofresh.delivery.repository.DeliveryAssignmentRepository;
import com.veggofresh.delivery.repository.DeliveryPartnerProfileRepository;
import com.veggofresh.delivery.service.DeliveryAccountDeletionService;
import com.veggofresh.payment.dto.WalletBalanceDto;
import com.veggofresh.payment.entity.PayoutRequestStatus;
import com.veggofresh.payment.repository.PayoutRequestRepository;
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
public class DeliveryAccountDeletionServiceImpl implements DeliveryAccountDeletionService {

    /** An assignment that is not finished yet -- "in flight" for this partner. */
    private static final List<DeliveryAssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = List.of(
            DeliveryAssignmentStatus.PENDING, DeliveryAssignmentStatus.ACCEPTED,
            DeliveryAssignmentStatus.ARRIVED_AT_STORE, DeliveryAssignmentStatus.PICKED_UP,
            DeliveryAssignmentStatus.ARRIVED_AT_DROP);

    /** Money "out the door but not settled" -- same reasoning as an un-withdrawn wallet balance. */
    private static final List<PayoutRequestStatus> IN_FLIGHT_PAYOUT_STATUSES =
            List.of(PayoutRequestStatus.PENDING, PayoutRequestStatus.APPROVED, PayoutRequestStatus.PROCESSING);

    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final WalletService walletService;
    private final PayoutRequestRepository payoutRequestRepository;
    private final UserAccountService userAccountService;

    @Override
    @Transactional
    public void deleteAccount(UUID userId, AccountDeletionRequestDto request) {
        if (!UserAccountService.ALLOWED_RETENTION_DAYS.contains(request.getRetentionDays())) {
            throw new BusinessException("ACCOUNT_DELETE_INVALID_RETENTION",
                    "Retention window must be one of " + UserAccountService.ALLOWED_RETENTION_DAYS + " days",
                    HttpStatus.BAD_REQUEST);
        }

        DeliveryPartnerProfile profile = deliveryPartnerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("DELIVERY_PROFILE_NOT_FOUND", "Delivery profile not found", HttpStatus.NOT_FOUND));

        // -- Pre-checks ---------------------------------------------------------
        if (deliveryAssignmentRepository.existsByDeliveryPartnerUserIdAndStatusIn(userId, ACTIVE_ASSIGNMENT_STATUSES)) {
            throw new BusinessException("ACCOUNT_DELETE_ACTIVE_ASSIGNMENT",
                    "You have a delivery in progress. Please complete it before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        WalletBalanceDto wallet = walletService.getBalance(userId);
        if (wallet.getBalance() != null && wallet.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("ACCOUNT_DELETE_WALLET_NOT_ZERO",
                    "Please withdraw your wallet balance before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        if (payoutRequestRepository.existsByUserIdAndStatusIn(userId, IN_FLIGHT_PAYOUT_STATUSES)) {
            throw new BusinessException("ACCOUNT_DELETE_PAYOUT_IN_FLIGHT",
                    "You have a payout request still being processed. Please wait for it to complete before deleting your account.",
                    HttpStatus.CONFLICT);
        }

        // -- Stats snapshot for the permanent audit log -------------------------
        Integer totalOrders = (int) deliveryAssignmentRepository
                .countByDeliveryPartnerUserIdAndStatus(userId, DeliveryAssignmentStatus.DELIVERED);

        // -- Hand off to Auth: soft-delete users row, revoke sessions, audit log --
        userAccountService.softDeleteAccount(userId, "DELIVERY", request.getRetentionDays(),
                request.getReason(), profile.getFullName(), totalOrders, null);

        // -- Soft-delete our own profile row. Personal / financial fields stay untouched here; they
        //    are only scrubbed once the window really expires (DeliveryAccountLifecycleListener). --
        profile.softDelete();
        deliveryPartnerProfileRepository.save(profile);

        log.info("Delivery partner self-deleted account: user={}, retentionDays={}", userId, request.getRetentionDays());
    }
}
