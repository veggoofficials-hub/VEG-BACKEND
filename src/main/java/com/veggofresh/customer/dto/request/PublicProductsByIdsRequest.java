package com.veggofresh.customer.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Guest wishlist lookup: the product ids a guest saved on the phone, plus the location to check them at.
 */
@Getter
@Setter
public class PublicProductsByIdsRequest {

    @NotNull(message = "latitude is required")
    private Double latitude;

    @NotNull(message = "longitude is required")
    private Double longitude;

    @NotEmpty(message = "productIds must not be empty")
    @Size(max = 100, message = "At most 100 productIds per request")
    private List<UUID> productIds;
}
