package com.veggofresh.customer.service;

import com.veggofresh.customer.entity.Cart;
import com.veggofresh.customer.entity.CartItem;
import com.veggofresh.customer.repository.CartItemRepository;
import com.veggofresh.customer.repository.CartRepository;
import com.veggofresh.payment.repository.PaymentOrderRepository;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.vendor.dto.ProductDto;
import com.veggofresh.vendor.service.ProductCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the cart re-arranging done when a customer changes address: the grouping rule on its own, then the real
 * split / merge behaviour on carts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartArrangerTest {

    private static final double LAT = 12.9716;
    private static final double LNG = 77.5946;

    private static final UUID SHOP_A = new UUID(0, 1);
    private static final UUID SHOP_B = new UUID(0, 2);

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private ProductCatalogService productCatalogService;

    private CartArranger arranger;
    private CartVendorResolver.Session session;
    private final UUID userId = UUID.randomUUID();

    /** Carts created by the arranger (the repository save assigns them an id). */
    private final List<Cart> createdCarts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        arranger = new CartArranger(cartRepository, cartItemRepository, paymentOrderRepository);
        session = new CartVendorResolver(productCatalogService).newSession(LAT, LNG);

        when(paymentOrderRepository.existsByUserIdAndStatusAndCreatedAtAfter(eq(userId), any(), any(Instant.class)))
                .thenReturn(false);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            if (cart.getId() == null) {
                cart.setId(UUID.randomUUID());
                createdCarts.add(cart);
            }
            return cart;
        });
    }

    // ── the grouping rule ───────────────────────────────────────────────────

    @Test
    @DisplayName("Products that one shop stocks all of end up in a single group")
    void oneShopCoversEverythingGivesOneGroup() {
        UUID p1 = new UUID(1, 1);
        UUID p2 = new UUID(1, 2);
        Map<UUID, Set<UUID>> in = new TreeMap<>();
        in.put(p1, Set.of(SHOP_A, SHOP_B));
        in.put(p2, Set.of(SHOP_A));

        List<Set<UUID>> groups = CartArranger.groupProducts(in);

        assertEquals(1, groups.size());
        assertEquals(Set.of(p1, p2), groups.get(0));
    }

    @Test
    @DisplayName("Products no single shop stocks together are split into separate groups")
    void noCommonShopGivesSeparateGroups() {
        UUID p1 = new UUID(1, 1);
        UUID p2 = new UUID(1, 2);
        Map<UUID, Set<UUID>> in = new TreeMap<>();
        in.put(p1, Set.of(SHOP_A));
        in.put(p2, Set.of(SHOP_B));

        List<Set<UUID>> groups = CartArranger.groupProducts(in);

        assertEquals(2, groups.size());
    }

    @Test
    @DisplayName("Grouping uses as few groups as the greedy rule finds, and every group shares a shop")
    void groupsAreFewAndEachHasACommonShop() {
        UUID p1 = new UUID(1, 1);
        UUID p2 = new UUID(1, 2);
        UUID p3 = new UUID(1, 3);
        Map<UUID, Set<UUID>> in = new TreeMap<>();
        in.put(p1, Set.of(SHOP_A));
        in.put(p2, Set.of(SHOP_A, SHOP_B));
        in.put(p3, Set.of(SHOP_B));

        List<Set<UUID>> groups = CartArranger.groupProducts(in);

        assertEquals(2, groups.size(), "three products and two shops never need three groups");
        Set<UUID> all = new HashSet<>();
        for (Set<UUID> group : groups) {
            Set<UUID> common = null;
            for (UUID product : group) {
                if (common == null) {
                    common = new HashSet<>(in.get(product));
                } else {
                    common.retainAll(in.get(product));
                }
                assertTrue(all.add(product), "a product must be in exactly one group");
            }
            assertFalse(common == null || common.isEmpty(), "every group must share at least one shop");
        }
        assertEquals(Set.of(p1, p2, p3), all);
    }

    @Test
    @DisplayName("The same data always gives the same groups, so switching address and back is stable")
    void groupingIsDeterministic() {
        Map<UUID, Set<UUID>> in = new TreeMap<>();
        in.put(new UUID(1, 1), Set.of(SHOP_A, SHOP_B));
        in.put(new UUID(1, 2), Set.of(SHOP_A, SHOP_B));
        in.put(new UUID(1, 3), Set.of(SHOP_B));

        assertEquals(CartArranger.groupProducts(in), CartArranger.groupProducts(in));
    }

    // ── arranging real carts ────────────────────────────────────────────────

    private void stockedBy(UUID productId, UUID... shops) {
        when(productCatalogService.getShopIdsForProduct(productId, LAT, LNG)).thenReturn(Set.of(shops));
        when(productCatalogService.findEligibleProductById(productId, LAT, LNG))
                .thenReturn(Optional.of(ProductDto.builder().id(productId).build()));
    }

    private void soldNowhere(UUID productId) {
        when(productCatalogService.getShopIdsForProduct(productId, LAT, LNG)).thenReturn(Set.of());
        when(productCatalogService.findEligibleProductById(productId, LAT, LNG)).thenReturn(Optional.empty());
    }

    private Cart cartOf(UUID... products) {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUserId(userId);
        for (UUID productId : products) {
            CartItem item = new CartItem();
            item.setId(UUID.randomUUID());
            item.setCart(cart);
            item.setProductId(productId);
            item.setQuantity(1);
            cart.getItems().add(item);
        }
        return cart;
    }

    private static Set<UUID> productsIn(Cart cart) {
        Set<UUID> ids = new HashSet<>();
        for (CartItem item : cart.getItems()) {
            ids.add(item.getProductId());
        }
        return ids;
    }

    @Test
    @DisplayName("A cart whose items no shop can supply together is split into two carts")
    void splitsACartThatNoShopCanFulfilWhole() {
        UUID tomatoes = new UUID(2, 1);
        UUID paneer = new UUID(2, 2);
        stockedBy(tomatoes, SHOP_A);
        stockedBy(paneer, SHOP_B);
        Cart cart = cartOf(tomatoes, paneer);
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(cart));

        arranger.arrange(userId, session);

        assertEquals(1, createdCarts.size(), "one new cart is needed");
        Cart fresh = createdCarts.get(0);
        assertEquals(1, cart.getItems().size());
        assertEquals(1, fresh.getItems().size());
        Set<UUID> all = new HashSet<>(productsIn(cart));
        all.addAll(productsIn(fresh));
        assertEquals(Set.of(tomatoes, paneer), all);
    }

    @Test
    @DisplayName("Two carts that one shop can now supply together are merged back into one")
    void mergesCartsThatOneShopCanNowFulfil() {
        UUID tomatoes = new UUID(2, 1);
        UUID paneer = new UUID(2, 2);
        stockedBy(tomatoes, SHOP_A);
        stockedBy(paneer, SHOP_A);
        Cart first = cartOf(tomatoes);
        Cart second = cartOf(paneer);
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(first, second));

        arranger.arrange(userId, session);

        assertEquals(Set.of(tomatoes, paneer), productsIn(first));
        assertTrue(second.getItems().isEmpty());
        assertTrue(second.isDeleted(), "the cart that was emptied must be retired");
        assertTrue(createdCarts.isEmpty());
    }

    @Test
    @DisplayName("An unavailable line is never moved or deleted by arranging; it stays where it was")
    void unavailableLinesStayPut() {
        UUID tomatoes = new UUID(2, 1);
        UUID paneer = new UUID(2, 2);
        UUID mango = new UUID(2, 3);
        stockedBy(tomatoes, SHOP_A);
        stockedBy(paneer, SHOP_B);
        soldNowhere(mango);
        Cart cart = cartOf(tomatoes, paneer, mango);
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(cart));

        arranger.arrange(userId, session);

        assertTrue(productsIn(cart).contains(mango), "the unavailable line must stay in its cart");
        for (Cart fresh : createdCarts) {
            assertFalse(productsIn(fresh).contains(mango));
        }
    }

    @Test
    @DisplayName("Carts that are already valid for the location, with no fewer carts possible, are left alone")
    void validCartsAreNotReshuffled() {
        UUID tomatoes = new UUID(2, 1);
        UUID paneer = new UUID(2, 2);
        stockedBy(tomatoes, SHOP_A);
        stockedBy(paneer, SHOP_B);
        Cart first = cartOf(tomatoes);
        Cart second = cartOf(paneer);
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(first, second));

        arranger.arrange(userId, session);

        assertEquals(Set.of(tomatoes), productsIn(first));
        assertEquals(Set.of(paneer), productsIn(second));
        assertTrue(createdCarts.isEmpty());
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    @Test
    @DisplayName("Arranging is refused while an online payment is waiting")
    void refusesWhilePaymentInProgress() {
        when(paymentOrderRepository.existsByUserIdAndStatusAndCreatedAtAfter(eq(userId), any(), any(Instant.class)))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> arranger.arrange(userId, session));

        assertEquals("PAYMENT_IN_PROGRESS", ex.getErrorCode());
        verify(cartRepository, never()).findByUserIdForUpdate(any());
    }
}
