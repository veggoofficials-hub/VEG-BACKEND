package com.veggofresh.archive.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** One archived thing (a bank account, a document, ...). The values are kept as JSON in {@code payload}. */
@Entity
@Table(name = "purged_account_archive_item")
@Getter
@Setter
public class PurgedAccountArchiveItem extends BaseEntity {

    /** Plain id of the {@link PurgedAccountArchive} header (no JPA relation on purpose). */
    @Column(name = "archive_id", nullable = false)
    private UUID archiveId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 40)
    private ArchiveItemType itemType;

    /** Id of the original row, when it had one. */
    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Cloudinary file kept for this item; removed only when an admin permanently deletes the archive. */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_public_id", length = 500)
    private String filePublicId;
}
