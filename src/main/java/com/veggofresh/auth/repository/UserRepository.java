package com.veggofresh.auth.repository;

import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.entity.UserRole;
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
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * The ACTIVE account of this phone number in one specific role.
     * A phone number can own up to three accounts (CUSTOMER, VENDOR, DELIVERY) but never two of the same
     * role (migration V164), so a phone-only lookup would be ambiguous and has been removed on purpose.
     */
    Optional<User> findByPhoneAndRole(String phone, UserRole role);

    /**
     * Purged users that still have bank accounts, KYC documents or change-request documents/values left in the
     * live tables (accounts purged before the archive existed). Columns: user id, role. Deliberately sees
     * deleted rows -- purged users and their shops are soft-deleted -- hence the IncludingDeleted name.
     */
    @Query(value = "SELECT u.id, u.role FROM users u WHERE u.is_purged = TRUE AND ("
            + " EXISTS (SELECT 1 FROM user_bank_accounts b WHERE b.user_id = u.id AND b.deleted_at IS NULL)"
            + " OR EXISTS (SELECT 1 FROM vendor_documents d JOIN vendor_shops s ON s.id = d.shop_id"
            + "            WHERE s.owner_user_id = u.id AND d.deleted_at IS NULL)"
            + " OR EXISTS (SELECT 1 FROM delivery_documents dd"
            + "            WHERE dd.delivery_partner_user_id = u.id AND dd.deleted_at IS NULL)"
            + " OR EXISTS (SELECT 1 FROM delivery_change_requests c"
            + "            WHERE c.delivery_partner_user_id = u.id AND c.deleted_at IS NULL"
            + "              AND (c.document_public_id IS NOT NULL OR c.previous_values IS NOT NULL"
            + "                   OR length(c.requested_values) > 2)))", nativeQuery = true)
    List<Object[]> findPurgedUsersWithLeftoverDataIncludingDeleted();
    Optional<User> findByEmail(String email);

    // ── Account deletion (migration V175) ───────────────────────────────────
    // The queries below are NATIVE on purpose: User carries @Where(deleted_at IS NULL), which would
    // otherwise hide exactly the soft-deleted rows these features need to see.

    /** Any account by id -- deleted or not, purged or not. */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingDeleted(@Param("id") UUID id);

    /**
     * The most recent SELF-deleted, not-yet-purged account of this phone number in one role -- i.e. the
     * account a returning person may get back. Rows an admin soft-deleted in the past have no purge_at
     * and are never returned, so they can never be restored by logging in. A purged row has its phone
     * scrubbed, so it is never matched either.
     */
    @Query(value = "SELECT * FROM users WHERE phone = :phone AND role = :role " +
                   "AND deleted_at IS NOT NULL AND purge_at IS NOT NULL AND is_purged = FALSE " +
                   "ORDER BY deleted_at DESC LIMIT 1", nativeQuery = true)
    Optional<User> findLatestSelfDeletedByPhoneAndRole(@Param("phone") String phone, @Param("role") String role);

    /** Every self-deleted account whose reactivation window has ended and that is not yet scrubbed. */
    @Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL AND is_purged = FALSE " +
                   "AND purge_at IS NOT NULL AND purge_at <= :now", nativeQuery = true)
    List<User> findExpiredUnpurgedAccounts(@Param("now") java.time.Instant now);

    /**
     * Accounts soft-deleted since {@code since}. Feeds the in-memory "logged-out-since" list that lets
     * the JWT filter refuse the access tokens of a deleted account (see SessionRevocationServiceImpl).
     */
    @Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL AND deleted_at >= :since", nativeQuery = true)
    List<User> findDeletedSince(@Param("since") java.time.Instant since);

    // ── Admin customer management queries ───────────────────────────────────

    /** Paginated list of all customers */
    Page<User> findByRole(UserRole role, Pageable pageable);

    /** Search customers by name/email/phone — used for ledger search bar */
    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchByRoleAndQuery(@Param("role") UserRole role,
                                    @Param("query") String query,
                                    Pageable pageable);

    /** Count by role */
    long countByRole(UserRole role);

    /** Count blocked accounts for a given role */
    long countByRoleAndIsBlocked(UserRole role, boolean isBlocked);

    /** All blocked customers — for moderation ledger */
    Page<User> findByRoleAndIsBlocked(UserRole role, boolean isBlocked, Pageable pageable);

    // ── Admin user-list APIs (Get-All with full filters) ────────────────────

    /**
     * Full-filter paginated query for admin user-list APIs.
     * Supports optional search (email / phone), optional isBlocked filter,
     * optional isVerified filter. Sorting & pagination are handled by Pageable.
     */
    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:isBlocked IS NULL OR u.isBlocked = :isBlocked) AND " +
           "(:isVerified IS NULL OR u.isVerified = :isVerified)")
    Page<User> findByRoleWithFilters(
            @Param("role") UserRole role,
            @Param("search") String search,
            @Param("isBlocked") Boolean isBlocked,
            @Param("isVerified") Boolean isVerified,
            Pageable pageable);

    /**
     * Non-paginated version — used to build the PDF export stream
     * (only exported pages worth of data, limited externally).
     */
    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:isBlocked IS NULL OR u.isBlocked = :isBlocked) AND " +
           "(:isVerified IS NULL OR u.isVerified = :isVerified)")
    List<User> findByRoleWithFiltersNoPage(
            @Param("role") UserRole role,
            @Param("search") String search,
            @Param("isBlocked") Boolean isBlocked,
            @Param("isVerified") Boolean isVerified,
            Pageable pageable);
}

