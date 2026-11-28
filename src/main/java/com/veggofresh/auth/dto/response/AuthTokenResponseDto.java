package com.veggofresh.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponseDto {
    private String accessToken;
    private String refreshToken;

    /**
     * {@code true} when an admin has blocked this account ("restricted mode"): the person is logged in
     * but cannot start new work. The app should show a banner with {@link #blockedReason}.
     */
    private boolean restricted;

    /** Why the account was blocked (shown to the person); {@code null} when not restricted. */
    private String blockedReason;

    private java.time.Instant blockedAt;

    /** {@code true} when new payout requests are on hold for this blocked account. */
    private boolean payoutsOnHold;
    /**
     * {@code true} ONLY on the login that just restored an account the person had deleted (inside the window
     * they chose). Show a one-time "Welcome back" message. Always {@code false} on normal logins and on
     * token refresh.
     */
    private boolean reactivated;
}
