package com.veggofresh.admin.controller;

import com.veggofresh.admin.dto.request.BlockAccountRequestDto;
import com.veggofresh.admin.dto.request.UnblockAccountRequestDto;
import com.veggofresh.admin.service.AdminAccountBlockService;
import com.veggofresh.auth.dto.response.AccountBlockLogResponseDto;
import com.veggofresh.auth.entity.UserRole;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin block / unblock for VENDORS and DELIVERY PARTNERS, plus the block history of all three roles.
 * (Customers keep their existing endpoints in AdminCustomerController.)
 *
 * <pre>
 * PUT /api/admin/vendors/{userId}/block              body {"reason": "...", "holdPayouts": false}
 * PUT /api/admin/vendors/{userId}/unblock            body (optional) {"note": "..."}
 * PUT /api/admin/delivery-partners/{userId}/block    body {"reason": "...", "holdPayouts": false}
 * PUT /api/admin/delivery-partners/{userId}/unblock  body (optional) {"note": "..."}
 * GET /api/admin/blocks?role=&action=&search=&currentOnly=&page=&size=
 * </pre>
 *
 * {@code userId} is the account's user id (the {@code userId} field of the admin vendor / delivery lists).
 * A block is "restricted mode": the person keeps their login and can finish work already in progress, but
 * cannot start new work. The reason is shown to the person.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountBlockController {

    private final AdminAccountBlockService adminAccountBlockService;

    @PutMapping("/vendors/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> blockVendor(@PathVariable UUID userId,
                                                         @Valid @RequestBody BlockAccountRequestDto request) {
        adminAccountBlockService.block(UserRole.VENDOR, userId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Vendor account blocked successfully"));
    }

    @PutMapping("/vendors/{userId}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblockVendor(@PathVariable UUID userId,
                                                           @Valid @RequestBody(required = false) UnblockAccountRequestDto request) {
        adminAccountBlockService.unblock(UserRole.VENDOR, userId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Vendor account unblocked successfully"));
    }

    @PutMapping("/delivery-partners/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> blockDeliveryPartner(@PathVariable UUID userId,
                                                                  @Valid @RequestBody BlockAccountRequestDto request) {
        adminAccountBlockService.block(UserRole.DELIVERY, userId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Delivery partner account blocked successfully"));
    }

    @PutMapping("/delivery-partners/{userId}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblockDeliveryPartner(@PathVariable UUID userId,
                                                                    @Valid @RequestBody(required = false) UnblockAccountRequestDto request) {
        adminAccountBlockService.unblock(UserRole.DELIVERY, userId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Delivery partner account unblocked successfully"));
    }

    /**
     * Block / unblock history, newest first. {@code currentOnly=true} keeps only the blocks that are in
     * force right now. {@code role}: CUSTOMER / VENDOR / DELIVERY. {@code action}: BLOCK / UNBLOCK.
     */
    @GetMapping("/blocks")
    public ResponseEntity<ApiResponse<Page<AccountBlockLogResponseDto>>> getBlockHistory(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean currentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AccountBlockLogResponseDto> history =
                adminAccountBlockService.history(role, action, search, currentOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Block history retrieved successfully"));
    }
}
