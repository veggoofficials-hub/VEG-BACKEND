package com.veggofresh.vendor.service.impl;

import com.veggofresh.admin.dto.response.CategoryResponseDto;
import com.veggofresh.admin.dto.response.ProductResponseDto;
import com.veggofresh.admin.dto.response.SubcategoryResponseDto;
import com.veggofresh.admin.service.AdminProductService;
import com.veggofresh.admin.service.CatalogCategoryService;
import com.veggofresh.admin.service.CatalogSubcategoryService;
import com.veggofresh.admin.service.PlatformSettingsService;
import com.veggofresh.platform.common.GeoDistance;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.vendor.dto.CategoryDto;
import com.veggofresh.vendor.dto.CategoryTreeDto;
import com.veggofresh.vendor.dto.ProductDto;
import com.veggofresh.vendor.dto.ServiceabilityDto;
import com.veggofresh.vendor.dto.ServiceabilityStatus;
import com.veggofresh.vendor.dto.ShopDto;
import com.veggofresh.vendor.dto.SubcategoryDto;
import com.veggofresh.vendor.dto.SubcategoryTreeDto;
import com.veggofresh.vendor.entity.KycStatus;
import com.veggofresh.vendor.entity.Shop;
import com.veggofresh.vendor.entity.VendorListing;
import com.veggofresh.vendor.repository.ShopRepository;
import com.veggofresh.vendor.repository.VendorListingRepository;
import com.veggofresh.vendor.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NEW ARCHITECTURE — catalog pivot (see NOTES_VENDOR.md). Rewritten to serve
 * Admin's master catalog through this shop's VendorListing rows, instead of
 * Vendor's old, now-deleted Product/Category entities.
 *
 * ⚠️ KNOWN SCALE CAVEAT: since eligibility (isListed + shop online/approved +
 * in-radius) can't be expressed inside Admin's searchProducts SQL query (that
 * would require Admin's repository to know about Vendor's VendorListing table,
 * a cross-module join we deliberately avoid), search/related/deals overfetch
 * a large page from Admin, filter eligibility + price range in Java, then
 * paginate in-memory. Correct at small-to-medium catalog size; revisit with a
 * real DB-level join (or a materialized/denormalized eligibility view) once
 * catalog size actually warrants it. See NOTES_VENDOR.md.
 *
 * RADIUS: the customer-facing shop radius is the admin-configured
 * PlatformSettings.deliveryRadiusKm (see resolveRadiusKm()) -- the same setting that
 * limits delivery partners -- so changing it in the admin panel changes which shops
 * and products customers see, and which vendors an order is sent to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

    /** Overfetch size for the Java-side filter+paginate workaround described above. */
    private static final int OVERFETCH_SIZE = 2000;

    /** Most products resolved by one getProductsWithAvailability call. */
    private static final int MAX_PRODUCTS_PER_LOOKUP = 100;

    private final AdminProductService adminProductService;
    private final CatalogCategoryService catalogCategoryService;
    private final CatalogSubcategoryService catalogSubcategoryService;
    private final VendorListingRepository vendorListingRepository;
    private final ShopRepository shopRepository;
    private final PlatformSettingsService platformSettingsService;

    @Override
    @Transactional(readOnly = true)
    public List<ShopDto> browseNearbyShops(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        double radiusKm = resolveRadiusKm();
        List<Shop> shops = shopRepository.findAllByDeletedAtIsNullAndIsOnlineTrueAndKycStatus(KycStatus.APPROVED);

        // A shop only counts when it has at least one listed product -- the same rule the product, category
        // and serviceability endpoints use, so /shops can no longer show a shop that has nothing to sell.
        Set<UUID> shopsWithListings = shopIdsWithListings(shops.stream().map(Shop::getId).collect(Collectors.toList()));

        return shops.stream()
                .filter(shop -> shop.getLatitude() != null && shop.getLongitude() != null)
                .filter(shop -> shopsWithListings.contains(shop.getId()))
                .map(shop -> new Object[]{shop, calculateDistanceInKm(latitude, longitude, shop.getLatitude(), shop.getLongitude())})
                .filter(pair -> (double) pair[1] <= radiusKm)
                .sorted(Comparator.comparingDouble(pair -> (double) pair[1]))
                .map(pair -> {
                    Shop shop = (Shop) pair[0];
                    double distance = (double) pair[1];
                    return ShopDto.builder()
                            .id(shop.getId())
                            .name(shop.getName())
                            .latitude(shop.getLatitude())
                            .longitude(shop.getLongitude())
                            .distanceInKm(distance)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryDto> browseCategories(String search, double latitude, double longitude, Pageable pageable) {
        GeoPoint.of(latitude, longitude);
        OrderableCatalog catalog = resolveOrderableCatalog(latitude, longitude, null);
        if (catalog.categoryIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        // The location filter has to run BEFORE paging (or page counts would be wrong), so the active categories
        // are read in one go (there are only a handful), filtered, and then paged in memory.
        List<CategoryDto> matching = catalogCategoryService
                .searchActiveCategories(search, PageRequest.of(0, OVERFETCH_SIZE, pageable.getSort()))
                .getContent().stream()
                .filter(c -> catalog.categoryIds().contains(c.getId()))
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
        return paginateInMemory(matching, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubcategoryDto> browseSubcategories(UUID categoryId, String search, double latitude, double longitude,
                                                     Pageable pageable) {
        GeoPoint.of(latitude, longitude);
        OrderableCatalog catalog = resolveOrderableCatalog(latitude, longitude, categoryId);
        if (catalog.subcategoryIds().isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<SubcategoryDto> matching = catalogSubcategoryService
                .searchActiveSubcategories(categoryId, search, PageRequest.of(0, OVERFETCH_SIZE, pageable.getSort()))
                .getContent().stream()
                .filter(s -> catalog.subcategoryIds().contains(s.getId()))
                .map(s -> SubcategoryDto.builder()
                        .id(s.getId())
                        .categoryId(s.getCategoryId())
                        .categoryName(s.getCategoryName())
                        .name(s.getName())
                        .imageUrl(s.getImageUrl())
                        .isActive(s.isActive())
                        .build())
                .collect(Collectors.toList());
        return paginateInMemory(matching, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDto> searchProducts(String query, UUID categoryId, UUID subcategoryId, Double minPrice, Double maxPrice,
                                            double latitude, double longitude, Pageable pageable) {
        GeoPoint.of(latitude, longitude);
        Page<ProductResponseDto> candidates = adminProductService.searchProducts(
                query, categoryId, subcategoryId, PageRequest.of(0, OVERFETCH_SIZE));

        List<ProductDto> eligible = candidates.getContent().stream()
                .filter(ProductResponseDto::isActive)
                .filter(p -> withinPriceRange(p.getPrice(), minPrice, maxPrice))
                .map(p -> toProductDtoIfEligible(p, latitude, longitude))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return paginateInMemory(eligible, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProductById(UUID catalogProductId, double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        // Must use the Optional-returning lookup, NOT getProductById inside a
        // try/catch. AdminProductServiceImpl is @Transactional and joins the
        // CALLER's transaction, so a RuntimeException escaping it makes Spring
        // mark that shared transaction rollback-only -- and no noRollbackFor on
        // this method can un-poison it. A caller that swallows our
        // BusinessException (CartServiceImpl.safeGetProduct) would then hit
        // UnexpectedRollbackException at commit. Returning Optional lets the
        // "not found" case be expressed as data, not as a failed nested
        // transaction.
        ProductResponseDto product = adminProductService.findProductById(catalogProductId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found or inactive");
        }

        return toProductDtoIfEligible(product, latitude, longitude)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "This product isn't available from any vendor near you right now"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findEligibleProductById(UUID catalogProductId, double latitude, double longitude) {
        // Pure read, no exceptions on the "not available" path -- see the
        // interface Javadoc for why that distinction is load-bearing for every
        // caller resolving products for rows that already exist (cart items,
        // wishlist entries, order lines).
        if (!GeoPoint.isValid(latitude, longitude)) {
            return Optional.empty();
        }
        return adminProductService.findProductById(catalogProductId)
                .filter(ProductResponseDto::isActive)
                .flatMap(product -> toProductDtoIfEligible(product, latitude, longitude));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getRelatedProducts(UUID catalogProductId, double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        // Same reasoning as getProductById: no exception-swallowing around a
        // nested @Transactional call, or this poisons the caller's transaction.
        ProductResponseDto product = adminProductService.findProductById(catalogProductId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Page<ProductResponseDto> candidates = adminProductService.searchProducts(
                null, null, product.getSubcategoryId(), PageRequest.of(0, OVERFETCH_SIZE));

        return candidates.getContent().stream()
                .filter(ProductResponseDto::isActive)
                .filter(p -> !p.getId().equals(catalogProductId))
                .map(p -> toProductDtoIfEligible(p, latitude, longitude))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getDailyDeals(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        Page<ProductResponseDto> candidates = adminProductService.searchProducts(
                null, null, null, PageRequest.of(0, OVERFETCH_SIZE));

        return candidates.getContent().stream()
                .filter(ProductResponseDto::isActive)
                // A "deal" = Admin has set a genuine discount (discountPercent is
                // only non-null when originalPrice > price -- see AdminProductServiceImpl
                // .computeDiscountPercent()).
                .filter(p -> p.getDiscountPercent() != null)
                .map(p -> toProductDtoIfEligible(p, latitude, longitude))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        OrderableCatalog catalog = resolveOrderableCatalog(latitude, longitude, null);
        if (catalog.categoryIds().isEmpty()) {
            return new ArrayList<>();
        }
        return catalogCategoryService.listCategories(false).stream()
                .filter(c -> catalog.categoryIds().contains(c.getId()))
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getShopIdsForProduct(UUID catalogProductId, double latitude, double longitude) {
        if (!GeoPoint.isValid(latitude, longitude)) {
            return Set.of();
        }
        double radiusKm = resolveRadiusKm();
        return vendorListingRepository.findByCatalogProductIdAndIsListedTrue(catalogProductId).stream()
                .map(VendorListing::getShop)
                .filter(shop -> isShopEligible(shop, latitude, longitude, radiusKm))
                .map(Shop::getId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryTreeDto getCategoryTree(UUID categoryId, double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        CategoryResponseDto category = catalogCategoryService.getCategoryById(categoryId);
        // Customers must never see an inactive category (same 404 as a category that does not exist).
        if (!category.isActive()) {
            throw new BusinessException("CATALOG_CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND);
        }
        return buildCategoryTree(category, latitude, longitude);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeDto> getFullCatalogTree(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        return catalogCategoryService.listCategories(false).stream()
                .map(category -> buildCategoryTree(category, latitude, longitude))
                .filter(tree -> tree.getSubcategories() != null && !tree.getSubcategories().isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceabilityDto checkServiceability(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        double radiusKm = resolveRadiusKm();
        List<Shop> approved = shopRepository.findAllByDeletedAtIsNullAndKycStatus(KycStatus.APPROVED);

        Double nearest = null;
        List<Shop> inRange = new ArrayList<>();
        for (Shop shop : approved) {
            if (shop.getLatitude() == null || shop.getLongitude() == null) continue;
            double distance = calculateDistanceInKm(latitude, longitude, shop.getLatitude(), shop.getLongitude());
            if (nearest == null || distance < nearest) {
                nearest = distance;
            }
            if (distance <= radiusKm) {
                inRange.add(shop);
            }
        }

        // A shop can serve right now only when it is online AND has at least one listed product.
        List<UUID> onlineIds = inRange.stream().filter(Shop::isOnline).map(Shop::getId).collect(Collectors.toList());
        int serving = shopIdsWithListings(onlineIds).size();

        ServiceabilityStatus status;
        String message;
        if (inRange.isEmpty()) {
            status = ServiceabilityStatus.NOT_SERVICEABLE;
            message = "Sorry, we don't deliver to this location yet.";
        } else if (serving == 0) {
            status = ServiceabilityStatus.TEMPORARILY_UNAVAILABLE;
            message = "We deliver to your area, but no shop is available right now. Please check back soon.";
        } else {
            status = ServiceabilityStatus.SERVICEABLE;
            message = "Good news! We deliver to your location.";
        }

        return ServiceabilityDto.builder()
                .status(status)
                .serviceable(status == ServiceabilityStatus.SERVICEABLE)
                .message(message)
                .radiusKm(radiusKm)
                .nearestShopDistanceKm(nearest)
                .shopsInRange(inRange.size())
                .servingShops(serving)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getOrderableProductIds(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        return orderableProductIdsAt(latitude, longitude);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getOrderableCategoryIds(double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        return resolveOrderableCatalog(latitude, longitude, null).categoryIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getProductsWithAvailability(Collection<UUID> productIds, double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        List<ProductDto> result = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        // De-duplicated, original order kept, capped so one request can never fan out without limit.
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID id : productIds) {
            if (id != null && ids.size() < MAX_PRODUCTS_PER_LOOKUP) {
                ids.add(id);
            }
        }
        for (UUID id : ids) {
            // Optional lookup, never an exception: see findEligibleProductById for why.
            Optional<ProductResponseDto> found = adminProductService.findProductById(id);
            if (found.isEmpty()) {
                continue;
            }
            ProductResponseDto product = found.get();
            if (!product.isActive()) {
                result.add(toUnavailableDto(product, "PRODUCT_UNAVAILABLE"));
                continue;
            }
            result.add(toProductDtoIfEligible(product, latitude, longitude)
                    .orElseGet(() -> toUnavailableDto(product, "NOT_AVAILABLE_AT_LOCATION")));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private CategoryTreeDto buildCategoryTree(CategoryResponseDto category, double latitude, double longitude) {
        List<SubcategoryTreeDto> subcategoryTrees = catalogSubcategoryService.listActiveByCategory(category.getId()).stream()
                .map(subcategory -> buildSubcategoryTree(subcategory, latitude, longitude))
                // A subcategory with nothing orderable at this location is not shown at all.
                .filter(tree -> tree.getProducts() != null && !tree.getProducts().isEmpty())
                .collect(Collectors.toList());

        return CategoryTreeDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .subcategories(subcategoryTrees)
                .build();
    }

    private SubcategoryTreeDto buildSubcategoryTree(SubcategoryResponseDto subcategory, double latitude, double longitude) {
        Page<ProductResponseDto> candidates = adminProductService.searchProducts(
                null, subcategory.getCategoryId(), subcategory.getId(), PageRequest.of(0, OVERFETCH_SIZE));

        List<ProductDto> products = candidates.getContent().stream()
                .filter(ProductResponseDto::isActive)
                .map(p -> toProductDtoIfEligible(p, latitude, longitude))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return SubcategoryTreeDto.builder()
                .id(subcategory.getId())
                .name(subcategory.getName())
                .imageUrl(subcategory.getImageUrl())
                .products(products)
                .build();
    }

    /** The admin-configured delivery radius (PlatformSettings.deliveryRadiusKm, cached in memory by the settings service). */
    private double resolveRadiusKm() {
        return platformSettingsService.getDeliveryRadiusKm();
    }

    /** Categories and subcategories that hold at least one product orderable at a location. */
    private record OrderableCatalog(Set<UUID> categoryIds, Set<UUID> subcategoryIds) {
        static final OrderableCatalog EMPTY = new OrderableCatalog(Set.of(), Set.of());
    }

    /**
     * Every catalog product that an approved, online, in-radius shop currently lists -- worked out with ONE
     * shop read and ONE listing query, instead of a listing lookup per product.
     */
    private Set<UUID> orderableProductIdsAt(double latitude, double longitude) {
        double radiusKm = resolveRadiusKm();
        List<UUID> shopIds = shopRepository.findAllByDeletedAtIsNullAndIsOnlineTrueAndKycStatus(KycStatus.APPROVED).stream()
                .filter(shop -> isShopEligible(shop, latitude, longitude, radiusKm))
                .map(Shop::getId)
                .collect(Collectors.toList());
        if (shopIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(vendorListingRepository.findListedCatalogProductIdsByShopIdIn(shopIds));
    }

    /**
     * The categories/subcategories that contain at least one orderable, active product at a location,
     * optionally limited to one category. The admin catalog never needs to know about listings: it is only
     * asked for products and the id set is intersected here.
     */
    private OrderableCatalog resolveOrderableCatalog(double latitude, double longitude, UUID categoryId) {
        Set<UUID> orderable = orderableProductIdsAt(latitude, longitude);
        if (orderable.isEmpty()) {
            return OrderableCatalog.EMPTY;
        }
        Page<ProductResponseDto> products = adminProductService.searchProducts(
                null, categoryId, null, PageRequest.of(0, OVERFETCH_SIZE));
        Set<UUID> categoryIds = new HashSet<>();
        Set<UUID> subcategoryIds = new HashSet<>();
        for (ProductResponseDto p : products.getContent()) {
            if (!p.isActive() || !orderable.contains(p.getId())) {
                continue;
            }
            if (p.getCategoryId() != null) {
                categoryIds.add(p.getCategoryId());
            }
            if (p.getSubcategoryId() != null) {
                subcategoryIds.add(p.getSubcategoryId());
            }
        }
        return new OrderableCatalog(categoryIds, subcategoryIds);
    }

    /** The subset of the given shops that has at least one listed product (one grouped query). */
    private Set<UUID> shopIdsWithListings(Collection<UUID> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new HashSet<>();
        for (Object[] row : vendorListingRepository.countGroupedByShopIdIn(shopIds)) {
            if (row[0] instanceof UUID id && row[1] instanceof Number count && count.longValue() > 0) {
                result.add(id);
            }
        }
        return result;
    }

    private CategoryDto toCategoryDto(CategoryResponseDto c) {
        return CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .iconUrl(c.getImageUrl())
                .isActive(c.isActive())
                .build();
    }

    /** A product shown on purpose although it cannot be ordered here (wishlist rows). No shop, flagged unavailable. */
    private ProductDto toUnavailableDto(ProductResponseDto product, String reason) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .description(product.getDescription())
                .category(product.getCategoryName())
                .imageUrl(product.getImageUrl())
                .imageUrls(product.getImageUrls())
                .unit(product.getUnit())
                .discountPercent(product.getDiscountPercent())
                .available(false)
                .unavailableReason(reason)
                .build();
    }

    private boolean isShopEligible(Shop shop, double latitude, double longitude, double radiusKm) {
        // No explicit soft-delete check needed here -- @Where(clause = "deleted_at IS
        // NULL") on Shop already excludes deleted rows at the query/relation level.
        if (shop == null) return false;
        if (!shop.isOnline() || shop.getKycStatus() != KycStatus.APPROVED) return false;
        if (shop.getLatitude() == null || shop.getLongitude() == null) return false;
        double distance = calculateDistanceInKm(latitude, longitude, shop.getLatitude(), shop.getLongitude());
        return distance <= radiusKm;
    }

    private boolean withinPriceRange(BigDecimal price, Double minPrice, Double maxPrice) {
        if (price == null) return false;
        if (minPrice != null && price.compareTo(BigDecimal.valueOf(minPrice)) < 0) return false;
        if (maxPrice != null && price.compareTo(BigDecimal.valueOf(maxPrice)) > 0) return false;
        return true;
    }

    /**
     * Resolves eligibility for one catalog product and, if eligible, maps it
     * to the cross-module ProductDto shape, picking the nearest eligible shop
     * as the single "representative" shop for display fields (shopId/shopName)
     * -- ProductDto has singular shop fields; getShopIdsForProduct is the real
     * multi-vendor-aware method Customer's cart logic actually keys off.
     */
    private Optional<ProductDto> toProductDtoIfEligible(ProductResponseDto product, double latitude, double longitude) {
        double radiusKm = resolveRadiusKm();

        List<VendorListing> listings = vendorListingRepository.findByCatalogProductIdAndIsListedTrue(product.getId());

        Shop nearestEligible = null;
        double nearestDistance = Double.MAX_VALUE;
        for (VendorListing listing : listings) {
            Shop shop = listing.getShop();
            if (!isShopEligible(shop, latitude, longitude, radiusKm)) continue;
            double distance = calculateDistanceInKm(latitude, longitude, shop.getLatitude(), shop.getLongitude());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEligible = shop;
            }
        }

        if (nearestEligible == null) {
            return Optional.empty();
        }

        return Optional.of(ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .description(product.getDescription())
                .shopId(nearestEligible.getId())
                .shopName(nearestEligible.getName())
                .category(product.getCategoryName())
                .imageUrl(product.getImageUrl())
                .imageUrls(product.getImageUrls())
                .unit(product.getUnit())
                .discountPercent(product.getDiscountPercent())
                // Not sourced from CatalogProduct -- separate, still-deferred
                // merchandising decisions (see NOTES_VENDOR.md), left as-is.
                .isBestSeller(false)
                .badge(null)
                .whyItsGreat(null)
                .storageTips(null)
                .build());
    }

    private <T> Page<T> paginateInMemory(List<T> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(new ArrayList<>(all.subList(start, end)), pageable, all.size());
    }

    /** Straight-line distance in km rounded to 0.1 (see {@link GeoDistance#distanceKmRounded}). */
    private double calculateDistanceInKm(double lat1, double lon1, double lat2, double lon2) {
        return GeoDistance.distanceKmRounded(lat1, lon1, lat2, lon2);
    }
}
