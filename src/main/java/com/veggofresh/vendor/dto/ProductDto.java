package com.veggofresh.vendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    /** null when there's no active discount -- copied straight from Admin's ProductResponseDto. */
    private BigDecimal originalPrice;
    private String description;
    private UUID shopId;
    private String shopName;
    private String category;
    /** Cover image -- kept for backward compatibility with anything only expecting one thumbnail. */
    private String imageUrl;

    /**
     * GAP FIX: full ordered gallery -- was missing entirely before this round, so even
     * though a product could have many images (Admin's ProductResponseDto.imageUrls
     * already returns all of them), only the single cover image ever reached the
     * customer-facing product screens. Now populated the same way imageUrl is.
     */
    private List<String> imageUrls;

    private String unit;
    private boolean isBestSeller;
    private Integer discountPercent;
    private String badge;
    private List<String> whyItsGreat;
    private String storageTips;

    /**
     * true for every product returned by browse/search. It is false only on rows that are shown on purpose
     * even though they cannot be ordered at the customer's current location (a saved wishlist item, or a
     * guest's locally stored wishlist). In that case {@code shopId}/{@code shopName}/{@code price}-related
     * ordering data is not usable and {@code unavailableReason} says why.
     */
    @Builder.Default
    private boolean available = true;

    /** null when {@code available}; otherwise NOT_AVAILABLE_AT_LOCATION or PRODUCT_UNAVAILABLE. */
    private String unavailableReason;
}
