package com.veggofresh.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Small block shown on a purged row of the admin Deleted Accounts screen. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveSummaryDto {
    /** ARCHIVED or PERMANENTLY_DELETED. */
    private String status;
    /** How many items are still archived (0 once permanently deleted). */
    private long itemCount;
    private Instant archivedAt;
    private Instant permanentlyDeletedAt;
}
