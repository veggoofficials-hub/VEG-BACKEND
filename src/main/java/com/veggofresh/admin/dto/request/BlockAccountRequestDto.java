package com.veggofresh.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code PUT /api/admin/{customers|vendors|delivery-partners}/{userId}/block}.
 * The reason is shown to the person (on login, in the restricted banner, in the push), so write it for them.
 */
@Getter
@Setter
public class BlockAccountRequestDto {

    @NotBlank(message = "A reason is required to block an account")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    /** Also refuse NEW payout requests while the account is blocked. Defaults to false. */
    private boolean holdPayouts;
}
