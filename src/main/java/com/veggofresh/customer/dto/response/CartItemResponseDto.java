package com.veggofresh.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponseDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private String unit;
    private int quantity;
    private BigDecimal subTotal;
    private String productImageUrl;

    /**
     * true when this line can be bought at the location the cart was requested for. false = show it greyed out
     * with a Remove button; it is NOT part of the cart's totals, itemCount or payable amount.
     */
    @Builder.Default
    private boolean available = true;

    /** null when available; otherwise NOT_AVAILABLE_AT_LOCATION or PRODUCT_UNAVAILABLE. */
    private String unavailableReason;
}
