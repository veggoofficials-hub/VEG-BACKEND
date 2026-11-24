package com.veggofresh.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload for {@code DELETE /api/customer/account}. No OTP/password re-verification --
 * per product decision, a click-to-confirm on the frontend is enough.
 */
@Getter
@Setter
public class AccountDeletionRequestDto {

    /**
     * How many days the account stays reactivatable by simply logging back in before it is permanently
     * scrubbed. Must be one of
     * {@link com.veggofresh.auth.service.UserAccountService#ALLOWED_RETENTION_DAYS}
     * (0 = delete immediately, no window at all).
     */
    @NotNull(message = "Retention window (in days) is required")
    private Integer retentionDays;

    /** Optional -- "not using the app" / "too expensive" / "bad experience" / "other", or free text. */
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
