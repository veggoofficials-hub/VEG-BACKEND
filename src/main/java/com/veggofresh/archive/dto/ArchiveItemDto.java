package com.veggofresh.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveItemDto {
    private UUID id;
    /** BANK_ACCOUNT, PROFILE_BANK_DETAILS, EMERGENCY_CONTACT, VENDOR_DOCUMENT, DELIVERY_DOCUMENT or CHANGE_REQUEST. */
    private String itemType;
    /** Id of the original row, when it had one. */
    private UUID sourceId;
    /** The archived values (keys depend on itemType). */
    private Map<String, Object> data;
    private String fileUrl;
    private Instant archivedAt;
}
