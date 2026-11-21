package com.veggofresh.customer.service;

import com.veggofresh.customer.dto.request.CartItemRequestDto;
import com.veggofresh.customer.dto.response.CartPromoPreviewDto;
import com.veggofresh.customer.dto.response.CartResponseDto;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.vendor.dto.ProductDto;

import java.util.List;
import java.util.UUID;

/**
 * PHASE 2 — NEW ARCHITECTURE, multi-cart model (PROJECT_STATE section 2).
 *
 * A customer can have several concurrent OPEN carts. Each cart tracks the
 * set of vendors able to fulfil ALL of its items at once (its candidate-vendor
 * set). Adding an item picks the cart that maximises vendor overlap with that
 * item — best-fit, not first-fit, so an item never gets stranded in a worse
 * cart just because that cart was created earlier. If no existing cart can
 * share a vendor with the item, a new cart is created.
 *
 * Two invariants this service guarantees:
 * <ul>
 *   <li>The candidate-vendor set is re-derived from the cart's items after
 *       every add, quantity change and removal, so it is never stale.</li>
 *   <li>A cart that is emptied is soft-deleted rather than left open, and
 *       empty carts are never returned. An open-but-empty cart used to be
 *       listed and labelled, which made two real carts appear as three.</li>
 * </ul>
 *
 * BREAKING CHANGE from Phase 1: every mutating method now returns the FULL
 * list of the user's open carts, not a single cart, since one call can
 * affect which cart an item lands in.
 *
 * LOCATION: every method that reads or changes carts takes the {@link GeoPoint} the customer is browsing from
 * (the app sends the selected address's coordinates). What is available, the vendor sets and the carts' grouping
 * are all judged at that point, never at a hidden default address. Lines that cannot be bought there are returned
 * flagged {@code available=false}, not hidden.
 */
public interface CartService {

    /** All of the user's currently open carts, oldest first ("Cart 1, Cart 2, ..."). */
    List<CartResponseDto> getOpenCarts(UUID userId, GeoPoint location);

    /** Adds an item; the system decides which existing cart it joins or creates a new one. */
    List<CartResponseDto> addItemToCart(UUID userId, CartItemRequestDto request, GeoPoint location);

    List<CartResponseDto> updateCartItem(UUID userId, UUID cartItemId, int quantity, GeoPoint location);

    List<CartResponseDto> removeCartItem(UUID userId, UUID cartItemId, GeoPoint location);

    /**
     * Re-arranges all open carts for the location into the fewest carts that one shop each can fulfil (see
     * {@code CartArranger}). Call it whenever the customer changes address, then show the returned carts.
     * Throws 409 PAYMENT_IN_PROGRESS while an online payment is waiting.
     */
    List<CartResponseDto> arrangeCarts(UUID userId, GeoPoint location);

    /** Soft-deletes a single cart (called after that cart successfully converts to an order). */
    void clearCart(UUID userId, UUID cartId);

    /** Soft-deletes every open cart for the user. */
    void clearAllCarts(UUID userId);

    /** Total item count summed across ALL open carts, for the badge. */
    int getCartCount(UUID userId);

    /** "Pairs well with" recommendations aggregated across all open carts. */
    List<ProductDto> getCartRecommendations(UUID userId, GeoPoint location);

    /** Applies a promo code to one specific cart (promo is per-cart, not checkout-wide). */
    List<CartResponseDto> applyPromoCode(UUID userId, UUID cartId, String code, GeoPoint location);

    List<CartResponseDto> removePromoCode(UUID userId, UUID cartId, GeoPoint location);

    /**
     * What the customer's coupon is worth for the given carts paid TOGETHER (all open carts when
     * {@code cartIds} is null or empty), valued exactly as the checkout summary and checkout value it. The
     * cart list shows each cart's coupon value alone; this is the combined view for the cart screen.
     *
     * @param couponCode optional: value this code instead of the one the customer is holding
     */
    CartPromoPreviewDto previewPromo(UUID userId, List<UUID> cartIds, String couponCode, GeoPoint location);
}
