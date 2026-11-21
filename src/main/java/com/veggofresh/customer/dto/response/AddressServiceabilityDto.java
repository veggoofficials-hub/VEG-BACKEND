package com.veggofresh.customer.dto.response;

import com.veggofresh.vendor.dto.ServiceabilityDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Serviceability of one saved address, for badging the address list ("We don't deliver here"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressServiceabilityDto {

    private UUID addressId;

    private ServiceabilityDto serviceability;
}
