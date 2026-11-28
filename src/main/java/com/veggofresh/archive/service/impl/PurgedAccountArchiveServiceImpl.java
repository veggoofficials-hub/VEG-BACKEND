package com.veggofresh.archive.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veggofresh.archive.dto.ArchiveDetailDto;
import com.veggofresh.archive.dto.ArchiveItemDto;
import com.veggofresh.archive.dto.ArchiveSummaryDto;
import com.veggofresh.archive.entity.ArchiveItemType;
import com.veggofresh.archive.entity.ArchiveStatus;
import com.veggofresh.archive.entity.PurgedAccountArchive;
import com.veggofresh.archive.entity.PurgedAccountArchiveItem;
import com.veggofresh.archive.repository.PurgedAccountArchiveItemRepository;
import com.veggofresh.archive.repository.PurgedAccountArchiveRepository;
import com.veggofresh.archive.service.PurgedAccountArchiveService;
import com.veggofresh.auth.entity.DeletedAccountLog;
import com.veggofresh.auth.repository.DeletedAccountLogRepository;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.platform.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurgedAccountArchiveServiceImpl implements PurgedAccountArchiveService {

    private final PurgedAccountArchiveRepository archiveRepository;
    private final PurgedAccountArchiveItemRepository itemRepository;
    private final DeletedAccountLogRepository deletedAccountLogRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    // ───────────────────────────── write side (purge time) ─────────────────────────────

    @Override
    @Transactional
    public boolean archiveItem(UUID userId, String role, ArchiveItemType type, UUID sourceId,
                               Map<String, Object> data, String fileUrl, String filePublicId) {
        PurgedAccountArchive archive = archiveRepository.findByUserId(userId)
                .orElseGet(() -> createHeader(userId, role));

        if (archive.getStatus() == ArchiveStatus.PERMANENTLY_DELETED) {
            // The admin already decided to delete this user's data for good: store nothing new.
            log.warn("Archive of user {} was already permanently deleted; not storing a new {} item", userId, type);
            return false;
        }

        PurgedAccountArchiveItem item = new PurgedAccountArchiveItem();
        item.setArchiveId(archive.getId());
        item.setItemType(type);
        item.setSourceId(sourceId);
        item.setPayload(toJson(data));
        item.setFileUrl(fileUrl);
        item.setFilePublicId(filePublicId);
        itemRepository.save(item);
        return true;
    }

    private PurgedAccountArchive createHeader(UUID userId, String role) {
        PurgedAccountArchive archive = new PurgedAccountArchive();
        archive.setUserId(userId);
        archive.setRole(role);
        archive.setStatus(ArchiveStatus.ARCHIVED);
        archive.setArchivedAt(Instant.now());
        // The deletion that is being purged is the user's latest log row.
        archive.setDeletedAccountLogId(deletedAccountLogRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(DeletedAccountLog::getId)
                .orElse(null));
        return archiveRepository.save(archive);
    }

    // ───────────────────────────── read side (admin) ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ArchiveSummaryDto summaryFor(DeletedAccountLog logEntry) {
        if (logEntry == null || logEntry.getPurgedAt() == null) {
            return null;
        }
        return archiveRepository.findByUserId(logEntry.getUserId())
                .map(a -> ArchiveSummaryDto.builder()
                        .status(a.getStatus().name())
                        .itemCount(itemRepository.countByArchiveId(a.getId()))
                        .archivedAt(a.getArchivedAt())
                        .permanentlyDeletedAt(a.getPermanentlyDeletedAt())
                        .build())
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchiveDetailDto getDetail(UUID deletedAccountLogId) {
        PurgedAccountArchive archive = findArchiveOfPurgedRow(deletedAccountLogId);
        return toDetail(archive, itemRepository.findByArchiveIdOrderByCreatedAtAscIdAsc(archive.getId()));
    }

    @Override
    @Transactional
    public ArchiveDetailDto permanentlyDelete(UUID deletedAccountLogId, UUID adminUserId, String note) {
        PurgedAccountArchive archive = findArchiveOfPurgedRow(deletedAccountLogId);

        if (archive.getStatus() == ArchiveStatus.PERMANENTLY_DELETED) {
            throw new BusinessException("ARCHIVE_ALREADY_DELETED",
                    "This archive was already permanently deleted", HttpStatus.CONFLICT);
        }

        List<PurgedAccountArchiveItem> items = itemRepository.findByArchiveIdOrderByCreatedAtAscIdAsc(archive.getId());
        for (PurgedAccountArchiveItem item : items) {
            cloudinaryService.deleteQuietly(item.getFilePublicId()); // no-op when there is no file
        }
        itemRepository.deleteAll(items);

        archive.setStatus(ArchiveStatus.PERMANENTLY_DELETED);
        archive.setPermanentlyDeletedAt(Instant.now());
        archive.setPermanentlyDeletedBy(adminUserId);
        archive.setDeletionNote(note == null || note.isBlank() ? null : note.trim());
        archiveRepository.save(archive);

        log.info("Archive {} of user {} permanently deleted by admin {} ({} item(s))",
                archive.getId(), archive.getUserId(), adminUserId, items.size());
        return toDetail(archive, Collections.emptyList());
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** The archive that belongs to a PURGED deletion-log row; 404 when the row is unknown, not purged, or has no archive. */
    private PurgedAccountArchive findArchiveOfPurgedRow(UUID deletedAccountLogId) {
        DeletedAccountLog logEntry = deletedAccountLogRepository.findById(deletedAccountLogId)
                .orElseThrow(() -> new BusinessException("DELETED_ACCOUNT_NOT_FOUND",
                        "Deleted account record not found", HttpStatus.NOT_FOUND));
        if (logEntry.getPurgedAt() == null) {
            throw new BusinessException("ARCHIVE_NOT_FOUND",
                    "This account has not been purged, so it has no archive", HttpStatus.NOT_FOUND);
        }
        return archiveRepository.findByUserId(logEntry.getUserId())
                .orElseThrow(() -> new BusinessException("ARCHIVE_NOT_FOUND",
                        "Nothing was archived for this account", HttpStatus.NOT_FOUND));
    }

    private ArchiveDetailDto toDetail(PurgedAccountArchive a, List<PurgedAccountArchiveItem> items) {
        List<ArchiveItemDto> dtos = new ArrayList<>();
        for (PurgedAccountArchiveItem i : items) {
            dtos.add(ArchiveItemDto.builder()
                    .id(i.getId())
                    .itemType(i.getItemType().name())
                    .sourceId(i.getSourceId())
                    .data(fromJson(i.getPayload()))
                    .fileUrl(i.getFileUrl())
                    .archivedAt(i.getCreatedAt())
                    .build());
        }
        return ArchiveDetailDto.builder()
                .archiveId(a.getId())
                .userId(a.getUserId())
                .role(a.getRole())
                .deletedAccountLogId(a.getDeletedAccountLogId())
                .status(a.getStatus().name())
                .archivedAt(a.getArchivedAt())
                .permanentlyDeletedAt(a.getPermanentlyDeletedAt())
                .permanentlyDeletedBy(a.getPermanentlyDeletedBy())
                .deletionNote(a.getDeletionNote())
                .items(dtos)
                .build();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Collections.emptyMap() : data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize archive payload", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            // A corrupt payload must never break the admin screen; show it raw instead.
            return Map.of("raw", json == null ? "" : json);
        }
    }
}
