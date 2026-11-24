package com.veggofresh.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One row of the block / unblock history. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBlockLogResponseDto {
    private UUID id;
    private UUID userId;
    private String role;
    /** BLOCK or UNBLOCK. */
    private String action;
    /** BLOCK: the reason the person sees. UNBLOCK: the optional admin note. */
    private String reason;
    private boolean payoutsHeld;
    private UUID adminId;
    private String adminLabel;
    private String phone;
    private String fullName;
    private Instant createdAt;
}
