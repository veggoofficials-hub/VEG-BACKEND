package com.veggofresh.admin.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Optional body of {@code PUT /api/admin/.../{userId}/unblock}. The note is shown to the person. */
@Getter
@Setter
public class UnblockAccountRequestDto {

    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
