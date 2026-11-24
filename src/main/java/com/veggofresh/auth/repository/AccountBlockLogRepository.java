package com.veggofresh.auth.repository;

import com.veggofresh.auth.entity.AccountBlockLog;
import com.veggofresh.auth.entity.BlockAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBlockLogRepository extends JpaRepository<AccountBlockLog, UUID> {

    long countByUserIdAndAction(UUID userId, BlockAction action);

    Optional<AccountBlockLog> findFirstByUserIdAndActionOrderByCreatedAtDesc(UUID userId, BlockAction action);

    /** How many times each of these users has been blocked (for the moderation screen's "flags raised"). */
    @Query("SELECT l.userId, COUNT(l) FROM AccountBlockLog l " +
           "WHERE l.userId IN :userIds AND l.action = :action GROUP BY l.userId")
    List<Object[]> countByUserIds(@Param("userIds") Collection<UUID> userIds, @Param("action") BlockAction action);

    /** Block rows of these users, newest first (caller keeps the first per user = the latest reason). */
    @Query("SELECT l FROM AccountBlockLog l " +
           "WHERE l.userId IN :userIds AND l.action = :action ORDER BY l.createdAt DESC")
    List<AccountBlockLog> findByUserIdsAndAction(@Param("userIds") Collection<UUID> userIds,
                                                 @Param("action") BlockAction action);

    /** Customers that were blocked at least once and are NOT blocked right now. */
    @Query("SELECT COUNT(DISTINCT l.userId) FROM AccountBlockLog l " +
           "WHERE l.role = :role AND l.action = :action " +
           "AND l.userId IN (SELECT u.id FROM User u WHERE u.isBlocked = false)")
    long countPreviouslyBlockedNowActive(@Param("role") String role, @Param("action") BlockAction action);

    /**
     * Admin history list. action: BLOCK | UNBLOCK (null = both). currentOnly=true keeps only BLOCK rows
     * that are still the newest event of their user, i.e. the blocks that are in force right now.
     */
    @Query(value = "SELECT l.* FROM account_block_log l " +
            "WHERE l.deleted_at IS NULL " +
            "AND (CAST(:role AS VARCHAR) IS NULL OR l.role = CAST(:role AS VARCHAR)) " +
            "AND (CAST(:action AS VARCHAR) IS NULL OR l.action = CAST(:action AS VARCHAR)) " +
            "AND (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
            "     OR LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.reason, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
            "AND (:currentOnly = FALSE OR (l.action = 'BLOCK' AND l.created_at = " +
            "     (SELECT MAX(l2.created_at) FROM account_block_log l2 WHERE l2.user_id = l.user_id))) " +
            "ORDER BY l.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM account_block_log l " +
            "WHERE l.deleted_at IS NULL " +
            "AND (CAST(:role AS VARCHAR) IS NULL OR l.role = CAST(:role AS VARCHAR)) " +
            "AND (CAST(:action AS VARCHAR) IS NULL OR l.action = CAST(:action AS VARCHAR)) " +
            "AND (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
            "     OR LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.reason, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
            "AND (:currentOnly = FALSE OR (l.action = 'BLOCK' AND l.created_at = " +
            "     (SELECT MAX(l2.created_at) FROM account_block_log l2 WHERE l2.user_id = l.user_id)))",
            nativeQuery = true)
    Page<AccountBlockLog> search(@Param("role") String role,
                                 @Param("action") String action,
                                 @Param("search") String search,
                                 @Param("currentOnly") boolean currentOnly,
                                 Pageable pageable);
}
