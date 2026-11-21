package com.veggofresh.customer.controller;

import com.veggofresh.banner.dto.CustomerBannerDto;
import com.veggofresh.banner.service.CustomerBannerService;
import com.veggofresh.customer.dto.request.PublicProductsByIdsRequest;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.platform.common.PageResponse;
import com.veggofresh.platform.common.TtlCache;
import com.veggofresh.platform.security.PublicRateLimiter;
import com.veggofresh.vendor.dto.CategoryDto;
import com.veggofresh.vendor.dto.ProductDto;
import com.veggofresh.vendor.dto.ServiceabilityDto;
import com.veggofresh.vendor.dto.ShopDto;
import com.veggofresh.vendor.dto.SubcategoryDto;
import com.veggofresh.vendor.service.ProductCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GUEST MODE: read-only browsing for someone who has not logged in. No JWT is needed, but
 * {@code X-Device-Id} is required on every call ({@code DeviceIdFilter}).
 *
 * <p>These endpoints call exactly the same services as the logged-in {@link CustomerBrowseController}, so a
 * guest sees the same shops, categories, subcategories and products for a location. Anything that needs an
 * account (cart, wishlist stored on the server, coupons, orders, chat) stays behind login.
 *
 * <p>Protection, because anyone can call these: a per-device and per-IP rate limit, page sizes capped at 50, and
 * a short response cache. For the cache the coordinates are rounded to about 100 m, so guest results are computed
 * at that precision (a shop right on the edge of the delivery radius can differ from the logged-in answer).
 */
@RestController
@RequestMapping("/api/public")
public class PublicBrowseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductCatalogService productCatalogService;
    private final CustomerBannerService customerBannerService;
    private final PublicRateLimiter rateLimiter;
    private final TtlCache cache;

    public PublicBrowseController(ProductCatalogService productCatalogService,
                                  CustomerBannerService customerBannerService,
                                  PublicRateLimiter rateLimiter,
                                  @Value("${veggofresh.public.cache-ttl-seconds:20}") long cacheTtlSeconds) {
        this.productCatalogService = productCatalogService;
        this.customerBannerService = customerBannerService;
        this.rateLimiter = rateLimiter;
        this.cache = new TtlCache(cacheTtlSeconds, 5000);
    }

    @GetMapping("/serviceability")
    public ResponseEntity<ApiResponse<ServiceabilityDto>> serviceability(
            HttpServletRequest http,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        ServiceabilityDto result = cache.get("serviceability|" + key(p),
                () -> productCatalogService.checkServiceability(p.latitude(), p.longitude()));
        return ok(result, "Serviceability retrieved successfully");
    }

    @GetMapping("/shops")
    public ResponseEntity<ApiResponse<List<ShopDto>>> shops(
            HttpServletRequest http,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        List<ShopDto> result = cache.get("shops|" + key(p),
                () -> productCatalogService.browseNearbyShops(p.latitude(), p.longitude()));
        return ok(result, "Shops retrieved successfully");
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<PageResponse<CategoryDto>>> categories(
            HttpServletRequest http,
            @RequestParam(required = false) String search,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        PageResponse<CategoryDto> result = cache.get(
                "categories|" + key(p) + "|" + search + "|" + safePage + "|" + safeSize,
                () -> {
                    Page<CategoryDto> categories = productCatalogService.browseCategories(
                            search, p.latitude(), p.longitude(), PageRequest.of(safePage, safeSize));
                    return PageResponse.of(categories);
                });
        return ok(result, "Categories retrieved successfully");
    }

    @GetMapping("/categories/all")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> allCategories(
            HttpServletRequest http,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        List<CategoryDto> result = cache.get("categories-all|" + key(p),
                () -> productCatalogService.getAllCategories(p.latitude(), p.longitude()));
        return ok(result, "Categories retrieved successfully");
    }

    @GetMapping("/categories/{categoryId}/subcategories")
    public ResponseEntity<ApiResponse<PageResponse<SubcategoryDto>>> subcategories(
            HttpServletRequest http,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        PageResponse<SubcategoryDto> result = cache.get(
                "subcategories|" + categoryId + "|" + key(p) + "|" + search + "|" + safePage + "|" + safeSize,
                () -> {
                    Page<SubcategoryDto> subcategories = productCatalogService.browseSubcategories(
                            categoryId, search, p.latitude(), p.longitude(), PageRequest.of(safePage, safeSize));
                    return PageResponse.of(subcategories);
                });
        return ok(result, "Subcategories retrieved successfully");
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> products(
            HttpServletRequest http,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID subcategoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        PageResponse<ProductDto> result = cache.get(
                "products|" + key(p) + "|" + query + "|" + categoryId + "|" + subcategoryId + "|" + minPrice + "|"
                        + maxPrice + "|" + safePage + "|" + safeSize,
                () -> {
                    Page<ProductDto> products = productCatalogService.searchProducts(
                            query, categoryId, subcategoryId, minPrice, maxPrice, p.latitude(), p.longitude(),
                            PageRequest.of(safePage, safeSize));
                    return PageResponse.of(products);
                });
        return ok(result, "Products retrieved successfully");
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> product(
            HttpServletRequest http,
            @PathVariable UUID id,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        ProductDto result = cache.get("product|" + id + "|" + key(p),
                () -> productCatalogService.getProductById(id, p.latitude(), p.longitude()));
        return ok(result, "Product retrieved successfully");
    }

    @GetMapping("/products/{id}/related")
    public ResponseEntity<ApiResponse<List<ProductDto>>> related(
            HttpServletRequest http,
            @PathVariable UUID id,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        List<ProductDto> result = cache.get("related|" + id + "|" + key(p),
                () -> productCatalogService.getRelatedProducts(id, p.latitude(), p.longitude()));
        return ok(result, "Related products retrieved successfully");
    }

    @GetMapping("/products/deals")
    public ResponseEntity<ApiResponse<List<ProductDto>>> deals(
            HttpServletRequest http,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        List<ProductDto> result = cache.get("deals|" + key(p),
                () -> productCatalogService.getDailyDeals(p.latitude(), p.longitude()));
        return ok(result, "Daily deals retrieved successfully");
    }

    /**
     * The guest wishlist lives on the phone (product ids only). The app sends those ids here to display them:
     * every id that still exists comes back, with {@code available=false} and a reason when it cannot be ordered at
     * the location. Not cached (the id list is part of the request body).
     */
    @PostMapping("/products/by-ids")
    public ResponseEntity<ApiResponse<List<ProductDto>>> productsByIds(
            HttpServletRequest http,
            @Valid @RequestBody PublicProductsByIdsRequest request) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(request.getLatitude(), request.getLongitude());
        List<ProductDto> result = productCatalogService.getProductsWithAvailability(
                request.getProductIds(), p.latitude(), p.longitude());
        return ok(result, "Products retrieved successfully");
    }

    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<List<CustomerBannerDto>>> banners(
            HttpServletRequest http,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        rateLimiter.check(http);
        GeoPoint p = GeoPoint.of(latitude, longitude).roundedToGrid();
        List<CustomerBannerDto> result = cache.get("banners|" + key(p),
                () -> customerBannerService.getGuestBanners(p.latitude(), p.longitude()));
        return ok(result, "Hero banners retrieved successfully");
    }

    private static String key(GeoPoint p) {
        return p.latitude() + "," + p.longitude();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }
}
