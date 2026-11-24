package com.veggofresh.admin.service.impl;

import com.veggofresh.admin.dto.request.BlockAccountRequestDto;
import com.veggofresh.admin.dto.request.UnblockAccountRequestDto;
import com.veggofresh.admin.service.AdminAccountBlockService;
import com.veggofresh.auth.dto.response.AccountBlockLogResponseDto;
import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.auth.service.AccountBlockService;
import com.veggofresh.customer.repository.CustomerProfileRepository;
import com.veggofresh.delivery.repository.DeliveryPartnerProfileRepository;
import com.veggofresh.vendor.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccountBlockServiceImpl implements AdminAccountBlockService {

    private final AccountBlockService accountBlockService;
    private final CustomerProfileRepository customerProfileRepository;
    private final ShopRepository shopRepository;
    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;

    @Override
    @Transactional
    public void block(UserRole role, UUID userId, BlockAccountRequestDto request, UUID adminId) {
        accountBlockService.block(userId, role, request.getReason(), request.isHoldPayouts(),
                displayName(role, userId), adminId);
    }

    @Override
    @Transactional
    public void unblock(UserRole role, UUID userId, UnblockAccountRequestDto request, UUID adminId) {
        String note = request == null ? null : request.getNote();
        accountBlockService.unblock(userId, role, note, displayName(role, userId), adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountBlockLogResponseDto> history(String role, String action, String search,
                                                    boolean currentOnly, Pageable pageable) {
        return accountBlockService.history(role, action, search, currentOnly, pageable);
    }

    /** The name the role module holds for this account, for the history row. May be null. */
    private String displayName(UserRole role, UUID userId) {
        return switch (role) {
            case CUSTOMER -> customerProfileRepository.findByUserId(userId)
                    .map(p -> p.getFullName()).orElse(null);
            case VENDOR -> shopRepository.findByOwnerUserIdAndDeletedAtIsNull(userId)
                    .map(s -> s.getFullName() != null ? s.getFullName() : s.getName()).orElse(null);
            case DELIVERY -> deliveryPartnerProfileRepository.findByUserId(userId)
                    .map(d -> d.getFullName()).orElse(null);
            default -> null;
        };
    }
}
