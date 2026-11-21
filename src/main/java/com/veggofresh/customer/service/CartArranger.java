package com.veggofresh.customer.service;

import com.veggofresh.customer.entity.Cart;
import com.veggofresh.customer.entity.CartItem;
import com.veggofresh.customer.repository.CartItemRepository;
import com.veggofresh.customer.repository.CartRepository;
import com.veggofresh.payment.entity.PaymentOrderStatus;
import com.veggofresh.payment.repository.PaymentOrderRepository;
import com.veggofresh.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Re-arranges ALL of a customer's open carts for a location, so that the customer always has the FEWEST carts in
 * which every cart can still be fulfilled by ONE shop (one cart is one order, and a shop takes a whole order).
 *
 * <p>Called when the customer changes address. Rules:
 * <ul>
 *   <li>Lines that cannot be bought at the location (no shop in range stocks them) are never moved to a new cart
 *       and never deleted: they stay in the cart they are in and are shown greyed out. (If their cart is closed
 *       because everything else moved out of it, they go to the first remaining cart.)</li>
 *   <li>Lines that can be bought are grouped greedily: repeatedly take the shop that can supply the most of the
 *       remaining lines. The result is deterministic, so switching to an address and back gives the same carts.
 *       It is a good approximation of the true minimum, not a proof of it.</li>
 *   <li>Existing carts are reused wherever possible (the cart that already holds most of a group keeps it), so
 *       "Cart 1 / Cart 2" stay as stable as they can. New carts are only created when there are more groups than
 *       carts; carts left empty are retired.</li>
 *   <li>If the carts are already valid for the location and no fewer carts are needed, nothing is moved.</li>
 *   <li>It refuses (409 PAYMENT_IN_PROGRESS) while an online payment for these carts is waiting, because the
 *       payment records the cart ids and the orders are created from them once it is authorised.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartArranger {

    /** How long a started-but-unfinished online payment is treated as "still waiting". */
    static final Duration PAYMENT_LOCK_WINDOW = Duration.ofMinutes(30);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    /** Must run inside the caller's transaction. */
    public void arrange(UUID userId, CartVendorResolver.Session vendors) {
        if (paymentOrderRepository.existsByUserIdAndStatusAndCreatedAtAfter(
                userId, PaymentOrderStatus.CREATED, Instant.now().minus(PAYMENT_LOCK_WINDOW))) {
            throw new BusinessException("PAYMENT_IN_PROGRESS",
                    "You have a payment in progress. Finish or cancel it before changing the address of your carts.",
                    HttpStatus.CONFLICT);
        }

        // Locked, so a checkout or a second arrange for the same customer waits instead of racing.
        List<Cart> carts = new ArrayList<>();
        for (Cart cart : cartRepository.findByUserIdForUpdate(userId)) {
            if (cart.hasItems()) {
                carts.add(cart);
            } else {
                cart.softDelete();
                cartRepository.save(cart);
            }
        }
        if (carts.isEmpty()) {
            return;
        }

        // The products that can be bought here, with the shops that stock each (sorted by id, so the result is
        // the same every time for the same data).
        Map<UUID, Set<UUID>> vendorsByProduct = new TreeMap<>();
        int cartsWithAvailableLines = 0;
        boolean allCartsValid = true;
        for (Cart cart : carts) {
            List<CartItem> available = vendors.availableItems(cart);
            if (available.isEmpty()) {
                continue;
            }
            cartsWithAvailableLines++;
            if (!vendors.canShipTogether(cart)) {
                allCartsValid = false;
            }
            for (CartItem item : available) {
                vendorsByProduct.put(item.getProductId(), vendors.vendorsFor(item.getProductId()));
            }
        }
        if (vendorsByProduct.isEmpty()) {
            return; // nothing can be bought here: leave every cart exactly as it is
        }

        List<Set<UUID>> groups = groupProducts(vendorsByProduct);

        if (allCartsValid && cartsWithAvailableLines <= groups.size()) {
            // Already a valid arrangement with no fewer carts possible: touch nothing but the stored vendor sets.
            for (Cart cart : carts) {
                vendors.resync(cart);
                cartRepository.save(cart);
            }
            return;
        }

        // ---- which cart takes which group ----
        List<Cart> targets = assignCarts(userId, carts, groups, vendors);

        // ---- move the available lines into their group's cart (one line per product, quantities summed) ----
        Map<UUID, List<CartItem>> availableLinesByProduct = new HashMap<>();
        for (Cart cart : carts) {
            for (CartItem item : vendors.availableItems(cart)) {
                availableLinesByProduct.computeIfAbsent(item.getProductId(), k -> new ArrayList<>()).add(item);
            }
        }
        for (int g = 0; g < groups.size(); g++) {
            Cart target = targets.get(g);
            for (UUID productId : groups.get(g)) {
                List<CartItem> lines = new ArrayList<>(availableLinesByProduct.get(productId));
                int total = lines.stream().mapToInt(CartItem::getQuantity).sum();
                CartItem keep = lines.stream().filter(line -> line.getCart() == target).findFirst().orElse(null);
                if (keep != null) {
                    if (keep.getQuantity() != total) {
                        keep.setQuantity(total);
                        cartItemRepository.save(keep);
                    }
                } else {
                    addLine(target, productId, total);
                }
                for (CartItem line : lines) {
                    if (line != keep) {
                        removeLine(line);
                    }
                }
            }
        }

        // ---- carts that took no group: their unavailable lines go to the first cart, then they are retired ----
        Set<Cart> used = new HashSet<>(targets);
        for (Cart cart : carts) {
            if (used.contains(cart)) {
                continue;
            }
            for (CartItem line : new ArrayList<>(cart.getItems())) {
                moveLine(line, targets.get(0));
            }
            if (!cart.hasItems()) {
                cart.softDelete();
                cartRepository.save(cart);
                log.info("Retired cart {} while re-arranging for a new location (its lines moved to other carts)",
                        cart.getId());
            }
        }

        for (Cart target : targets) {
            vendors.resync(target);
            cartRepository.save(target);
        }
    }

    /**
     * Greedy grouping: repeatedly pick the shop that can supply the most of the remaining products (ties go to the
     * smallest shop id, so the result is deterministic) and put every remaining product it stocks into one group.
     * Every product given here has at least one shop, so every group is non-empty and the loop ends.
     */
    static List<Set<UUID>> groupProducts(Map<UUID, Set<UUID>> vendorsByProduct) {
        Set<UUID> remaining = new LinkedHashSet<>(vendorsByProduct.keySet());
        List<Set<UUID>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Map<UUID, Integer> coverage = new HashMap<>();
            for (UUID productId : remaining) {
                for (UUID vendorId : vendorsByProduct.get(productId)) {
                    coverage.merge(vendorId, 1, Integer::sum);
                }
            }
            UUID best = null;
            int bestCount = 0;
            for (Map.Entry<UUID, Integer> entry : coverage.entrySet()) {
                int count = entry.getValue();
                if (count > bestCount || (count == bestCount && best != null && entry.getKey().compareTo(best) < 0)) {
                    best = entry.getKey();
                    bestCount = count;
                }
            }
            if (best == null) {
                break; // defensive: a product with no shop at all cannot be grouped
            }
            Set<UUID> group = new LinkedHashSet<>();
            for (UUID productId : remaining) {
                if (vendorsByProduct.get(productId).contains(best)) {
                    group.add(productId);
                }
            }
            groups.add(group);
            remaining.removeAll(group);
        }
        return groups;
    }

    /**
     * Picks the cart that will hold each group: first the cart that already holds most of the group's products,
     * then any cart that is left over, and only then a brand-new cart. Returns one cart per group, same order.
     */
    private List<Cart> assignCarts(UUID userId, List<Cart> carts, List<Set<UUID>> groups,
                                   CartVendorResolver.Session vendors) {
        record Candidate(int overlap, int cartIndex, int groupIndex) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int c = 0; c < carts.size(); c++) {
            Set<UUID> inCart = new HashSet<>();
            for (CartItem item : vendors.availableItems(carts.get(c))) {
                inCart.add(item.getProductId());
            }
            for (int g = 0; g < groups.size(); g++) {
                int overlap = 0;
                for (UUID productId : groups.get(g)) {
                    if (inCart.contains(productId)) {
                        overlap++;
                    }
                }
                if (overlap > 0) {
                    candidates.add(new Candidate(overlap, c, g));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::overlap).reversed()
                .thenComparingInt(Candidate::cartIndex)
                .thenComparingInt(Candidate::groupIndex));

        Cart[] targets = new Cart[groups.size()];
        boolean[] cartUsed = new boolean[carts.size()];
        for (Candidate cand : candidates) {
            if (targets[cand.groupIndex()] == null && !cartUsed[cand.cartIndex()]) {
                targets[cand.groupIndex()] = carts.get(cand.cartIndex());
                cartUsed[cand.cartIndex()] = true;
            }
        }
        for (int g = 0; g < targets.length; g++) {
            if (targets[g] != null) {
                continue;
            }
            for (int c = 0; c < carts.size(); c++) {
                if (!cartUsed[c]) {
                    targets[g] = carts.get(c);
                    cartUsed[c] = true;
                    break;
                }
            }
            if (targets[g] == null) {
                Cart fresh = new Cart();
                fresh.setUserId(userId);
                targets[g] = cartRepository.save(fresh);
            }
        }
        return new ArrayList<>(List.of(targets));
    }

    /** A new line in {@code cart} (a moved line is always deleted and re-created, never re-parented). */
    private void addLine(Cart cart, UUID productId, int quantity) {
        CartItem line = new CartItem();
        line.setCart(cart);
        line.setProductId(productId);
        line.setQuantity(quantity);
        cart.getItems().add(line);
        cartItemRepository.save(line);
    }

    private void removeLine(CartItem line) {
        line.getCart().getItems().remove(line);
        cartItemRepository.delete(line);
    }

    /** Moves a line to another cart, adding to the quantity if that cart already has the same product. */
    private void moveLine(CartItem line, Cart destination) {
        if (line.getCart() == destination) {
            return;
        }
        CartItem same = destination.getItems().stream()
                .filter(existing -> existing.getProductId().equals(line.getProductId()))
                .findFirst().orElse(null);
        if (same != null) {
            same.setQuantity(same.getQuantity() + line.getQuantity());
            cartItemRepository.save(same);
        } else {
            addLine(destination, line.getProductId(), line.getQuantity());
        }
        removeLine(line);
    }
}
