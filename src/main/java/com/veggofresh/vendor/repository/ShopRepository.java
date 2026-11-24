package com.veggofresh.vendor.repository;

import com.veggofresh.vendor.entity.KycStatus;
import com.veggofresh.vendor.entity.Shop;
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
public interface ShopRepository extends JpaRepository<Shop, UUID> {
    Optional<Shop> findByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);

    /**
     * The shop of this owner including a soft-deleted one. Native on purpose: {@link Shop} carries
     * {@code @Where(deleted_at IS NULL)}, which would otherwise hide the very row the account-deletion
     * lifecycle (reactivate / purge) has to reach.
     */
    @Query(value = "SELECT * FROM vendor_shops WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC LIMIT 1",
           nativeQuery = true)
    Optional<Shop> findByOwnerUserIdIncludingDeleted(@Param("ownerUserId") UUID ownerUserId);
    Optional<Shop> findByIdAndDeletedAtIsNull(UUID id);
    List<Shop> findAllByDeletedAtIsNullAndIsOnlineTrueAndKycStatus(KycStatus kycStatus);

    /** Every approved, non-deleted shop whether it is online or not (used by the serviceability check). */
    List<Shop> findAllByDeletedAtIsNullAndKycStatus(KycStatus kycStatus);

    /**
     * NEW -- "awaiting review" means kycStatus PENDING AND the application has actually
     * been submitted (applicationSubmittedAt set) -- PENDING alone also matches shops
     * still mid-onboarding with nothing yet to review. Same distinction Delivery's
     * equivalent query makes. Powers VendorKycServiceImpl / Admin's KYC review queue.
     */
    Page<Shop> findByKycStatusAndApplicationSubmittedAtIsNotNull(KycStatus kycStatus, Pageable pageable);

    // ── Admin Get-All-Vendors with full filters ──────────────────────────────

    /**
     * Paginated query with optional filters: search (name/email/businessPhone/city),
     * kycStatus, isOnline. Sorting is handled by Pageable.
     * Excludes soft-deleted records (deletedAt IS NULL enforced by @Where on entity).
     */
    @Query("SELECT s FROM Shop s WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.businessPhone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:kycStatus IS NULL OR s.kycStatus = :kycStatus) AND " +
           "(:isOnline IS NULL OR s.isOnline = :isOnline)")
    Page<Shop> findWithFilters(
            @Param("search") String search,
            @Param("kycStatus") KycStatus kycStatus,
            @Param("isOnline") Boolean isOnline,
            Pageable pageable);

    /**
     * Non-paginated version for PDF export — caller applies a size limit via Pageable.
     */
    @Query("SELECT s FROM Shop s WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.businessPhone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:kycStatus IS NULL OR s.kycStatus = :kycStatus) AND " +
           "(:isOnline IS NULL OR s.isOnline = :isOnline)")
    List<Shop> findWithFiltersNoPage(
            @Param("search") String search,
            @Param("kycStatus") KycStatus kycStatus,
            @Param("isOnline") Boolean isOnline,
            Pageable pageable);
}

