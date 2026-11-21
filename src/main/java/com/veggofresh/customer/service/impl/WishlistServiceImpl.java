package com.veggofresh.customer.service.impl;

import com.veggofresh.customer.entity.Wishlist;
import com.veggofresh.customer.repository.WishlistRepository;
import com.veggofresh.customer.service.WishlistService;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.vendor.dto.ProductDto;
import com.veggofresh.vendor.service.ProductCatalogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LOCATION: the location now comes from the request (the place the customer is browsing from), not from a
 * hidden default address. A saved item that cannot be ordered at that location is returned with
 * {@code available=false} and a reason instead of being silently hidden, so a customer never thinks their list
 * lost something.
 *
 * <p>No try/catch around catalog lookups on purpose: a swallowed exception from a nested
 * {@code @Transactional} call marks this transaction rollback-only. The catalog's
 * {@code getProductsWithAvailability} reports "not available" as data, never as an exception.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductCatalogService productCatalogService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getWishlist(UUID userId, GeoPoint location) {
        List<UUID> productIds = wishlistRepository.findByUserId(userId).stream()
                .map(Wishlist::getProductId)
                .collect(Collectors.toList());
        return productCatalogService.getProductsWithAvailability(productIds, location.latitude(), location.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getWishlistByCategory(UUID userId, String category, GeoPoint location) {
        List<ProductDto> wishlist = getWishlist(userId, location);
        if (category == null || category.trim().isEmpty()) {
            return wishlist;
        }
        return wishlist.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category.trim()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getWishlistRecommendations(UUID userId, GeoPoint location) {
        // Only the items that can be ordered here seed the recommendations.
        List<ProductDto> wishlist = getWishlist(userId, location).stream()
                .filter(ProductDto::isAvailable)
                .collect(Collectors.toList());
        if (wishlist.isEmpty()) {
            return productCatalogService.getDailyDeals(location.latitude(), location.longitude());
        }

        List<ProductDto> recommendations = new ArrayList<>();
        for (ProductDto p : wishlist) {
            // No try/catch -- a swallowed exception from a nested
            // @Transactional method still marks this transaction rollback-only
            // and resurfaces as UnexpectedRollbackException at commit.
            List<ProductDto> related = productCatalogService.getRelatedProducts(p.getId(), location.latitude(),
                    location.longitude());
            if (related != null) {
                for (ProductDto r : related) {
                    if (recommendations.stream().noneMatch(rec -> rec.getId().equals(r.getId()))) {
                        recommendations.add(r);
                    }
                }
            }
        }
        return recommendations.stream().limit(6).collect(Collectors.toList());
    }

    @Override
    public void addToWishlist(UUID userId, UUID productId, GeoPoint location) {
        ProductDto product = productCatalogService.getProductById(productId, location.latitude(), location.longitude());
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found in catalog", HttpStatus.BAD_REQUEST);
        }

        // Serialise concurrent adds for the SAME customer + product (e.g. a double tap on the
        // heart icon). The second request waits here until the first one commits, then its
        // check below sees the committed row and it simply does nothing -- instead of both
        // requests passing the check and the second INSERT failing with a duplicate-key 500.
        wishlistRepository.lockForAdd(userId + ":" + productId);

        // The repository hides soft-deleted rows (@Where deleted_at IS NULL), so a product the
        // customer removed earlier is correctly treated as "not in wishlist" and is added again.
        // This is safe because V163 made the uniqueness rule apply to ACTIVE rows only.
        boolean alreadyInWishlist = wishlistRepository.findByUserIdAndProductId(userId, productId).isPresent();
        if (!alreadyInWishlist) {
            Wishlist wishlist = new Wishlist();
            wishlist.setUserId(userId);
            wishlist.setProductId(productId);
            wishlistRepository.save(wishlist);
        }
    }

    @Override
    public void removeFromWishlist(UUID userId, UUID productId) {
        Wishlist wishlist = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new BusinessException("WISHLIST_ITEM_NOT_FOUND", "Item not found in your wishlist", HttpStatus.NOT_FOUND));

        wishlist.softDelete();
        wishlistRepository.save(wishlist);
    }
}
