package com.veggofresh.archive.service;

import com.veggofresh.archive.dto.ArchiveDetailDto;
import com.veggofresh.archive.dto.ArchiveSummaryDto;
import com.veggofresh.archive.entity.ArchiveItemType;
import com.veggofresh.auth.entity.DeletedAccountLog;

import java.util.Map;
import java.util.UUID;

/**
 * The archive of data removed when an account is purged (bank accounts, KYC documents, ...).
 * Data goes in at purge time; only an admin can later delete it for good.
 */
public interface PurgedAccountArchiveService {

    /**
     * Stores one removed item in the user's archive (creating the archive header on first use).
     * Runs inside the caller's transaction, so if it fails the whole purge rolls back and is retried.
     *
     * @return true when the item was stored; false when an admin had already permanently deleted this
     *         user's archive -- the caller must then remove the file itself and keep nothing.
     */
    boolean archiveItem(UUID userId, String role, ArchiveItemType type, UUID sourceId,
                        Map<String, Object> data, String fileUrl, String filePublicId);

    /** Archive status for a Deleted Accounts row, or null when the row is not purged / nothing was archived. */
    ArchiveSummaryDto summaryFor(DeletedAccountLog logEntry);

    /** Full archive (all items) of the purged deletion-log row {@code deletedAccountLogId}. */
    ArchiveDetailDto getDetail(UUID deletedAccountLogId);

    /**
     * Admin decision: delete the archived data for good -- Cloudinary files and item rows. The header is
     * kept and stamped with who did it, when, and the note.
     */
    ArchiveDetailDto permanentlyDelete(UUID deletedAccountLogId, UUID adminUserId, String note);
}
