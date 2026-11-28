package com.veggofresh.archive.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One header row per purged user. Linked to the rest of the system by plain ids only
 * (user_id, deleted_account_log_id) -- never by a JPA relation -- so it can never cause a
 * "row not found" failure elsewhere.
 */
@Entity
@Table(name = "purged_account_archive")
@Getter
@Setter
public class PurgedAccountArchive extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** The deleted_account_log row of the deletion that was purged (latest one for the user). */
    @Column(name = "deleted_account_log_id")
    private UUID deletedAccountLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ArchiveStatus status = ArchiveStatus.ARCHIVED;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @Column(name = "permanently_deleted_at")
    private Instant permanentlyDeletedAt;

    @Column(name = "permanently_deleted_by")
    private UUID permanentlyDeletedBy;

    @Column(name = "deletion_note", length = 500)
    private String deletionNote;
}
