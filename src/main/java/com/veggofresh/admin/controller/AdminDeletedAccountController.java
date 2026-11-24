package com.veggofresh.admin.controller;

import com.veggofresh.admin.dto.response.DeletedAccountResponseDto;
import com.veggofresh.admin.service.AdminDeletedAccountService;
import com.veggofresh.platform.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin archive of accounts their owners deleted themselves -- who, when, why, and what became of the
 * account (still in its window, came back, was purged, or came back as a new account).
 *
 * <pre>
 * GET /api/admin/deleted-accounts?role=&search=&status=&page=&size=
 * GET /api/admin/deleted-accounts/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/deleted-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeletedAccountController {

    private final AdminDeletedAccountService adminDeletedAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DeletedAccountResponseDto>>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                adminDeletedAccountService.list(role, search, status, pageable),
                "Deleted accounts retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeletedAccountResponseDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                adminDeletedAccountService.getById(id),
                "Deleted account retrieved successfully"));
    }
}
