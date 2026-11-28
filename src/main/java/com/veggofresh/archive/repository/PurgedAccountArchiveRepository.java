package com.veggofresh.archive.repository;

import com.veggofresh.archive.entity.PurgedAccountArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurgedAccountArchiveRepository extends JpaRepository<PurgedAccountArchive, UUID> {

    /** At most one archive per user (unique index). */
    Optional<PurgedAccountArchive> findByUserId(UUID userId);
}
