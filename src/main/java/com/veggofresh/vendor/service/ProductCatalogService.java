package com.veggofresh.vendor.service;

import com.veggofresh.vendor.dto.CategoryDto;
import com.veggofresh.vendor.dto.CategoryTreeDto;
import com.veggofresh.vendor.dto.ProductDto;
import com.veggofresh.vendor.dto.ServiceabilityDto;
import com.veggofresh.vendor.dto.ShopDto;
import com.veggofresh.vendor.dto.SubcategoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module catalog API used by the Customer side. Everything the customer sees (shops, categories,
 * subcategories, products) is filtered by LOCATION: a product is orderable at a location only when an
 * approved, online shop within the admin-configured delivery radius has it listed. Categories and
 * subcategories are shown only when they hold at least one such product.
 */
public interface ProductCatalogService {

    /**
     * Approved, online shops within the delivery radius that have at least one listed product, nearest first.
     */
    List<ShopDto> browseNearbyShops(double latitude, double longitude);

    /** Active categories that contain at least one product orderable at this location. */
    Page<CategoryDto> browseCategories(String search, double latitude, double longitude, Pageable pageable);

    /** Active subcategories of the category that contain at least one product orderable at this location. */
    Page<SubcategoryDto> browseSubcategories(UUID categoryId, String search, double latitude, double longitude,
                                              Pageable pageable);

    Page<ProductDto> searchProducts(String query, UUID categoryId, UUID subcategoryId, Double minPrice, Double maxPrice,
                                     double latitude, double longitude, Pageable pageable);

    ProductDto getProductById(UUID catalogProductId, double latitude, double longitude);

    /**
     * Non-throwing lookup: the product when it is orderable at the location, otherwise empty.
     *
     * <p>Load-bearing: callers resolving products for rows that already exist (cart items, wishlist entries,
     * order lines) must use this, not {@link #getProductById}. A swallowed exception from a nested
     * {@code @Transactional} call marks the caller's transaction rollback-only. Invalid coordinates simply
     * give an empty result here and never throw, for the same reason.
     */
    java.util.Optional<ProductDto> findEligibleProductById(UUID catalogProductId, double latitude, double longitude);

    List<ProductDto> getRelatedProducts(UUID catalogProductId, double latitude, double longitude);

    List<ProductDto> getDailyDeals(double latitude, double longitude);

    /** All active categories that contain at least one product orderable at this location (unpaged). */
    List<CategoryDto> getAllCategories(double latitude, double longitude);

    /** The approved, online, in-radius shops that list this product (empty for invalid coordinates). */
    Set<UUID> getShopIdsForProduct(UUID catalogProductId, double latitude, double longitude);

    /** One category with its subcategories; subcategories that hold no orderable product are left out. */
    CategoryTreeDto getCategoryTree(UUID categoryId, double latitude, double longitude);

    /** Every category/subcategory/product orderable at this location; empty ones are left out. */
    List<CategoryTreeDto> getFullCatalogTree(double latitude, double longitude);

    /**
     * Do we deliver here? SERVICEABLE, TEMPORARILY_UNAVAILABLE (approved shops in range but none can serve
     * right now) or NOT_SERVICEABLE (no approved shop in range at all).
     */
    ServiceabilityDto checkServiceability(double latitude, double longitude);

    /** Every catalog product id orderable at this location (listed by an approved, online, in-radius shop). */
    Set<UUID> getOrderableProductIds(double latitude, double longitude);

    /** Every active category id that holds at least one product orderable at this location. */
    Set<UUID> getOrderableCategoryIds(double latitude, double longitude);

    /**
     * Resolves the given products for display at a location, keeping the order of {@code productIds} and
     * returning EVERY id that still exists: orderable ones with {@code available=true}, the rest with
     * {@code available=false} and a reason (so a wishlist can grey them out instead of silently losing them).
     * Ids that no longer exist at all are skipped. At most 100 ids are resolved.
     */
    List<ProductDto> getProductsWithAvailability(Collection<UUID> productIds, double latitude, double longitude);
}
