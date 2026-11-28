package com.veggofresh.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveDetailDto {
    private UUID archiveId;
    private UUID userId;
    private String role;
    private UUID deletedAccountLogId;
    /** ARCHIVED or PERMANENTLY_DELETED. */
    private String status;
    private Instant archivedAt;
    private Instant permanentlyDeletedAt;
    private UUID permanentlyDeletedBy;
    private String deletionNote;
    /** Empty once the archive has been permanently deleted. */
    private List<ArchiveItemDto> items;
}
