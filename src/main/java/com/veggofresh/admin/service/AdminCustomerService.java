package com.veggofresh.admin.service;

import com.veggofresh.admin.dto.AdminCustomerLedgerDto;
import com.veggofresh.admin.dto.CustomerBaseStatsDto;
import com.veggofresh.admin.dto.ModerationLedgerRowDto;
import com.veggofresh.admin.dto.ModerationStatsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Admin customer screens.
 *
 * <p>There is deliberately NO admin delete: accounts are only ever deleted by their owner (see
 * CustomerAccountDeletionService). Blocking and unblocking -- for customers, vendors and delivery
 * partners alike -- lives in {@link AdminAccountBlockService}.
 */
public interface AdminCustomerService {

    // ── Customer Base (View Customers screen) ───────────────────────────────

    CustomerBaseStatsDto getCustomerBaseStats();

    Page<AdminCustomerLedgerDto> getCustomerLedger(String search, Pageable pageable);

    AdminCustomerLedgerDto getCustomerById(UUID userId);

    // ── Moderation Center (Block / Unblock Accounts screen) ─────────────────

    ModerationStatsDto getModerationStats();

    Page<ModerationLedgerRowDto> getModerationLedger(String search, Pageable pageable);
}
