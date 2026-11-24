package com.veggofresh.admin.service;

import com.veggofresh.admin.dto.request.BlockAccountRequestDto;
import com.veggofresh.admin.dto.request.UnblockAccountRequestDto;
import com.veggofresh.auth.dto.response.AccountBlockLogResponseDto;
import com.veggofresh.auth.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Admin block / unblock of a Customer, Vendor or Delivery account -- one entry point for all three roles.
 * The block itself (reason, history, events) is done by Auth's {@code AccountBlockService}; this class only
 * finds the person's display name for the history row and enforces that the endpoint's role matches.
 */
public interface AdminAccountBlockService {

    void block(UserRole role, UUID userId, BlockAccountRequestDto request, UUID adminId);

    void unblock(UserRole role, UUID userId, UnblockAccountRequestDto request, UUID adminId);

    Page<AccountBlockLogResponseDto> history(String role, String action, String search,
                                             boolean currentOnly, Pageable pageable);
}
