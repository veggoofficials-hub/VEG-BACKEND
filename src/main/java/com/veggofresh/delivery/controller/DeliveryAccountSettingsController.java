package com.veggofresh.delivery.controller;

import com.veggofresh.delivery.dto.request.AccountSettingsRequestDto;
import com.veggofresh.delivery.dto.response.AccountSettingsResponseDto;
import com.veggofresh.delivery.service.DeliveryProfileService;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.veggofresh.delivery.dto.request.AccountDeletionRequestDto;
import com.veggofresh.delivery.service.DeliveryAccountDeletionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/delivery/account-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELIVERY')")
public class DeliveryAccountSettingsController {

    private final DeliveryProfileService deliveryProfileService;
    private final DeliveryAccountDeletionService deliveryAccountDeletionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AccountSettingsResponseDto>> getSettings() {
        var settings = deliveryProfileService.getAccountSettings(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(settings, "Account settings retrieved successfully"));
    }

    /**
     * Updates account settings, including the personal avatar, in one call. Send as
     * {@code multipart/form-data}; every field is optional (PATCH semantics) -- include
     * just {@code avatar} to change only the photo, exactly like sending just
     * {@code fullName} changes only the name. Replacing the photo auto-deletes the old
     * one from Cloudinary.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AccountSettingsResponseDto>> updateSettings(
            @Valid @ModelAttribute AccountSettingsRequestDto request) {
        var settings = deliveryProfileService.updateAccountSettings(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(settings, "Account settings updated successfully"));
    }

    /**
     * Self-service account deletion. Refused (409) if a delivery is still in progress, the wallet is not
     * zero or a payout is still being processed, and (403) if the account is blocked. No OTP / password
     * re-verification -- the frontend's confirm dialog is the only gate before this call.
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@Valid @RequestBody AccountDeletionRequestDto request) {
        deliveryAccountDeletionService.deleteAccount(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Account deletion request completed successfully"));
    }
}
