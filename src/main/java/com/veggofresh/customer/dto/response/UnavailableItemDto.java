package com.veggofresh.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One cart line that cannot be delivered to the chosen address (shown greyed out with a Remove button). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnavailableItemDto {

    /** The cart line id: pass it to DELETE /api/customer/carts/items/{id} to remove the line. */
    private UUID cartItemId;

    private UUID productId;

    private String productName;

    private String productImageUrl;

    private int quantity;

    /** NOT_AVAILABLE_AT_LOCATION (no shop in range stocks it) or PRODUCT_UNAVAILABLE (removed or deactivated). */
    private String reason;
}
