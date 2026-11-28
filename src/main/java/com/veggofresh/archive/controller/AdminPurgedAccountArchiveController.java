package com.veggofresh.archive.controller;

import com.veggofresh.archive.dto.ArchiveDetailDto;
import com.veggofresh.archive.dto.PermanentDeleteRequestDto;
import com.veggofresh.archive.service.PurgedAccountArchiveService;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin view of the data that was removed from a PURGED account (bank accounts, KYC documents, ...).
 * {id} is the id of the Deleted Accounts row (the same id as GET /api/admin/deleted-accounts/{id}).
 */
@RestController
@RequestMapping("/api/admin/deleted-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurgedAccountArchiveController {

    private final PurgedAccountArchiveService archiveService;

    @GetMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<ArchiveDetailDto>> getArchive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getDetail(id), "Archive retrieved successfully"));
    }

    @PostMapping("/{id}/archive/permanent-delete")
    public ResponseEntity<ApiResponse<ArchiveDetailDto>> permanentDelete(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PermanentDeleteRequestDto request) {
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(ApiResponse.success(
                archiveService.permanentlyDelete(id, SecurityUtils.getCurrentUserId(), note),
                "Archive permanently deleted"));
    }
}
