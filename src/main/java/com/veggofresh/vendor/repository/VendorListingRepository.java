package com.veggofresh.vendor.repository;

import com.veggofresh.vendor.entity.VendorListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorListingRepository extends JpaRepository<VendorListing, UUID> {

    Optional<VendorListing> findByShopIdAndCatalogProductId(UUID shopId, UUID catalogProductId);

    List<VendorListing> findByShopId(UUID shopId);

    long countByShopIdAndIsListedTrue(UUID shopId);

    /** Batch count of active listings grouped by shop ID. */
    @Query("SELECT vl.shop.id, COUNT(vl) FROM VendorListing vl WHERE vl.shop.id IN :shopIds AND vl.isListed = true GROUP BY vl.shop.id")
    List<Object[]> countGroupedByShopIdIn(@Param("shopIds") Collection<UUID> shopIds);

    /** All shops (via their listing rows) currently listing this catalog product. */
    List<VendorListing> findByCatalogProductIdAndIsListedTrue(UUID catalogProductId);

    /**
     * Listed rows for a catalog product WITH their shop loaded and only where that shop is not soft-deleted.
     * Use this instead of findByCatalogProductIdAndIsListedTrue + listing.getShop(): the lazy shop proxy
     * throws EntityNotFoundException ("Unable to find Shop with id ...") for a soft-deleted shop, because
     * Shop is filtered by @Where(deleted_at IS NULL). The inner join (optional = false) plus the explicit
     * s.deletedAt check drops such rows in SQL, and JOIN FETCH means no proxy is ever involved.
     */
    @Query("SELECT vl FROM VendorListing vl JOIN FETCH vl.shop s " +
           "WHERE vl.catalogProductId = :catalogProductId AND vl.isListed = true AND s.deletedAt IS NULL")
    List<VendorListing> findListedWithLiveShopByCatalogProductId(@Param("catalogProductId") UUID catalogProductId);

    /**
     * Vendor deleted their account: switch off every currently-listed row of the shop and flag it, so
     * reactivation can bring back exactly these. Rows the vendor had already unlisted are not touched.
     * Native on purpose (one statement, no entity loading); bumps version/updated_at like a normal save.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE vendor_listings SET is_listed = FALSE, unlisted_by_account_deletion = TRUE, " +
                   "updated_at = NOW(), version = version + 1 " +
                   "WHERE shop_id = :shopId AND is_listed = TRUE AND deleted_at IS NULL", nativeQuery = true)
    int unlistAllForAccountDeletion(@Param("shopId") UUID shopId);

    /** Vendor reactivated within the window: relist exactly the rows unlistAllForAccountDeletion switched off. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE vendor_listings SET is_listed = TRUE, unlisted_by_account_deletion = FALSE, " +
                   "updated_at = NOW(), version = version + 1 " +
                   "WHERE shop_id = :shopId AND unlisted_by_account_deletion = TRUE AND deleted_at IS NULL", nativeQuery = true)
    int relistAfterReactivation(@Param("shopId") UUID shopId);

    /** Every catalog product that at least one of the given shops currently lists (one query, no per-product lookups). */
    @Query("SELECT DISTINCT vl.catalogProductId FROM VendorListing vl WHERE vl.shop.id IN :shopIds AND vl.isListed = true")
    List<UUID> findListedCatalogProductIdsByShopIdIn(@Param("shopIds") Collection<UUID> shopIds);

    /**
     * Paginated listings for /mine, with an optional isListed filter:
     *   null  -> ALL (both listed and unlisted rows -- unlisting no longer
     *            drops an item out of "mine", only DELETE does that)
     *   true  -> LISTED only
     *   false -> UNLISTED only
     * Soft-deleted rows are already excluded by the entity's
     * @Where(clause = "deleted_at IS NULL"), so no extra check is needed here.
     * isListed is a Boolean (not a raw string), so this doesn't hit the same
     * null-bind-parameter issue that :search does in CatalogProductRepository --
     * it's a plain boolean-column comparison, unambiguous to Postgres.
     */
    @Query("SELECT v FROM VendorListing v WHERE v.shop.id = :shopId AND (:isListed IS NULL OR v.isListed = :isListed)")
    Page<VendorListing> findByShopIdAndOptionalListedStatus(@Param("shopId") UUID shopId,
                                                             @Param("isListed") Boolean isListed,
                                                             Pageable pageable);
}
