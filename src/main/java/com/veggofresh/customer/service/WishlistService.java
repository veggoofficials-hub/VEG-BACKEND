package com.veggofresh.customer.service;

import java.util.List;
import java.util.UUID;

import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.vendor.dto.ProductDto;

/**
 * The wishlist is saved per customer and has no location of its own. Every read and the add take the
 * location the customer is browsing from; items that cannot be ordered there are NOT hidden or deleted, they are
 * returned with {@code available=false} and a reason, so the app can grey them out.
 */
public interface WishlistService {

    /** Every saved item, in the order it was saved; unavailable ones are flagged, not dropped. */
    List<ProductDto> getWishlist(UUID userId, GeoPoint location);

    List<ProductDto> getWishlistByCategory(UUID userId, String category, GeoPoint location);

    /** Related products for the items that can be ordered here; today's deals when none can. */
    List<ProductDto> getWishlistRecommendations(UUID userId, GeoPoint location);

    /** Adds a product; it must be orderable at {@code location} (the place the customer is browsing from). */
    void addToWishlist(UUID userId, UUID productId, GeoPoint location);

    void removeFromWishlist(UUID userId, UUID productId);
}
