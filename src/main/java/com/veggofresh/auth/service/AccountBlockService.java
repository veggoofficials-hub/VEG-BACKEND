package com.veggofresh.auth.service;

import com.veggofresh.auth.dto.response.AccountBlockLogResponseDto;
import com.veggofresh.auth.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Admin block / unblock of a Customer, Vendor or Delivery account, with a reason and a permanent history.
 * A block is "restricted mode" (see {@link AccountAccessService}), not a lock-out.
 */
public interface AccountBlockService {

    /**
     * @param userId        the account to block
     * @param expectedRole  the role the calling endpoint is for (a customer endpoint can't block a vendor)
     * @param reason        shown to the person; required
     * @param holdPayouts   also refuse NEW payout requests while blocked
     * @param fullName      the role module's display name for the account (for the history row), may be null
     * @param adminId       the admin performing the action
     * @throws com.veggofresh.platform.exception.BusinessException 404 user not found, 400 wrong role,
     *         409 already blocked
     */
    void block(UUID userId, UserRole expectedRole, String reason, boolean holdPayouts, String fullName, UUID adminId);

    /**
     * @param note optional admin note, shown to the person in the unblock notification
     * @throws com.veggofresh.platform.exception.BusinessException 404 / 400 / 409 not blocked
     */
    void unblock(UUID userId, UserRole expectedRole, String note, String fullName, UUID adminId);

    /** Block / unblock history, newest first. */
    Page<AccountBlockLogResponseDto> history(String role, String action, String search,
                                             boolean currentOnly, Pageable pageable);
}
