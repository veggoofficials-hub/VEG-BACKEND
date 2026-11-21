package com.veggofresh.customer.controller;

import com.veggofresh.customer.dto.response.AddressServiceabilityDto;
import com.veggofresh.customer.entity.Address;
import com.veggofresh.customer.repository.AddressRepository;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.platform.exception.BusinessException;
import com.veggofresh.platform.security.SecurityUtils;
import com.veggofresh.vendor.dto.ServiceabilityDto;
import com.veggofresh.vendor.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Do you deliver here?" for a logged-in customer. The same answer the guest endpoint
 * ({@code GET /api/public/serviceability}) gives, plus the ability to check a saved address.
 */
@RestController
@RequestMapping("/api/customer/serviceability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerServiceabilityController {

    private final ProductCatalogService productCatalogService;
    private final AddressRepository addressRepository;

    /**
     * Pass EITHER {@code addressId} (one of the customer's saved addresses) OR both {@code latitude} and
     * {@code longitude}.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<ServiceabilityDto>> check(
            @RequestParam(required = false) UUID addressId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        double lat;
        double lng;
        if (addressId != null) {
            Address address = addressRepository.findByIdAndUserId(addressId, SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new BusinessException("ADDRESS_NOT_FOUND", "Address not found",
                            HttpStatus.NOT_FOUND));
            lat = address.getLatitude();
            lng = address.getLongitude();
        } else if (latitude != null && longitude != null) {
            lat = latitude;
            lng = longitude;
        } else {
            throw new BusinessException("LOCATION_REQUIRED",
                    "Provide either addressId or both latitude and longitude", HttpStatus.BAD_REQUEST);
        }
        GeoPoint point = GeoPoint.of(lat, lng);
        return ResponseEntity.ok(ApiResponse.success(
                productCatalogService.checkServiceability(point.latitude(), point.longitude()),
                "Serviceability retrieved successfully"));
    }

    /** Serviceability of every saved address, so the address list can badge the ones we do not deliver to. */
    @GetMapping("/addresses")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AddressServiceabilityDto>>> checkAllAddresses() {
        List<AddressServiceabilityDto> result = new ArrayList<>();
        for (Address address : addressRepository.findByUserId(SecurityUtils.getCurrentUserId())) {
            GeoPoint point = GeoPoint.of(address.getLatitude(), address.getLongitude());
            result.add(AddressServiceabilityDto.builder()
                    .addressId(address.getId())
                    .serviceability(productCatalogService.checkServiceability(point.latitude(), point.longitude()))
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.success(result, "Address serviceability retrieved successfully"));
    }
}
