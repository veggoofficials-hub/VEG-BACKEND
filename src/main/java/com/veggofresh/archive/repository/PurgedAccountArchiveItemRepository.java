package com.veggofresh.archive.repository;

import com.veggofresh.archive.entity.PurgedAccountArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurgedAccountArchiveItemRepository extends JpaRepository<PurgedAccountArchiveItem, UUID> {

    List<PurgedAccountArchiveItem> findByArchiveIdOrderByCreatedAtAscIdAsc(UUID archiveId);

    long countByArchiveId(UUID archiveId);
}
