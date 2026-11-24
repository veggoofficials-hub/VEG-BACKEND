package com.veggofresh.auth.repository;

import com.veggofresh.auth.entity.DeletedAccountLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletedAccountLogRepository extends JpaRepository<DeletedAccountLog, UUID> {

    /** The log row of this user's current, still-open deletion (not reactivated, not purged). */
    Optional<DeletedAccountLog> findFirstByUserIdAndReactivatedAtIsNullAndPurgedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** How many times this phone + role has been self-deleted (the log is never purged). */
    long countByPhoneAndRole(String phone, String role);

    Optional<DeletedAccountLog> findFirstByPhoneAndRoleOrderByCreatedAtDesc(String phone, String role);

    /** Newest deletion first; used to flag "previously deleted" on the admin user screens. */
    List<DeletedAccountLog> findByPhoneInAndRoleOrderByCreatedAtDesc(java.util.Collection<String> phones, String role);

    /**
     * Admin list. Native SQL on purpose: "returned as a new account" is an EXISTS over the users table
     * (an ACTIVE account with the same phone + role), and the CAST(... AS VARCHAR) wrappers keep
     * PostgreSQL happy when a filter is null.
     *
     * <p>status: IN_WINDOW | REACTIVATED | PURGED | RETURNED_AS_NEW (null = all).
     */
    @Query(value = "SELECT l.* FROM deleted_account_log l " +
            "WHERE l.deleted_at IS NULL " +
            "AND (CAST(:role AS VARCHAR) IS NULL OR l.role = CAST(:role AS VARCHAR)) " +
            "AND (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
            "     OR LOWER(l.phone) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.email, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
            "AND (CAST(:status AS VARCHAR) IS NULL " +
            "     OR (CAST(:status AS VARCHAR) = 'IN_WINDOW' AND l.reactivated_at IS NULL AND l.purged_at IS NULL) " +
            "     OR (CAST(:status AS VARCHAR) = 'REACTIVATED' AND l.reactivated_at IS NOT NULL) " +
            "     OR (CAST(:status AS VARCHAR) = 'PURGED' AND l.purged_at IS NOT NULL AND l.reactivated_at IS NULL " +
            "         AND NOT EXISTS (SELECT 1 FROM users u WHERE u.phone = l.phone AND u.role = l.role AND u.deleted_at IS NULL)) " +
            "     OR (CAST(:status AS VARCHAR) = 'RETURNED_AS_NEW' AND l.purged_at IS NOT NULL AND l.reactivated_at IS NULL " +
            "         AND EXISTS (SELECT 1 FROM users u WHERE u.phone = l.phone AND u.role = l.role AND u.deleted_at IS NULL))) " +
            "ORDER BY l.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM deleted_account_log l " +
            "WHERE l.deleted_at IS NULL " +
            "AND (CAST(:role AS VARCHAR) IS NULL OR l.role = CAST(:role AS VARCHAR)) " +
            "AND (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
            "     OR LOWER(l.phone) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.full_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
            "     OR LOWER(COALESCE(l.email, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
            "AND (CAST(:status AS VARCHAR) IS NULL " +
            "     OR (CAST(:status AS VARCHAR) = 'IN_WINDOW' AND l.reactivated_at IS NULL AND l.purged_at IS NULL) " +
            "     OR (CAST(:status AS VARCHAR) = 'REACTIVATED' AND l.reactivated_at IS NOT NULL) " +
            "     OR (CAST(:status AS VARCHAR) = 'PURGED' AND l.purged_at IS NOT NULL AND l.reactivated_at IS NULL " +
            "         AND NOT EXISTS (SELECT 1 FROM users u WHERE u.phone = l.phone AND u.role = l.role AND u.deleted_at IS NULL)) " +
            "     OR (CAST(:status AS VARCHAR) = 'RETURNED_AS_NEW' AND l.purged_at IS NOT NULL AND l.reactivated_at IS NULL " +
            "         AND EXISTS (SELECT 1 FROM users u WHERE u.phone = l.phone AND u.role = l.role AND u.deleted_at IS NULL)))",
            nativeQuery = true)
    Page<DeletedAccountLog> search(@Param("role") String role,
                                   @Param("search") String search,
                                   @Param("status") String status,
                                   Pageable pageable);
}
