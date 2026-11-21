package com.veggofresh.banner.controller;

import com.veggofresh.banner.dto.CustomerBannerDto;
import com.veggofresh.banner.service.CustomerBannerService;
import com.veggofresh.platform.common.ApiResponse;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The hero banners of the customer app. Same URL as before; the fields id, title, subtitle and imageUrl are
 * unchanged. NEW: {@code latitude} and {@code longitude} are required, because a banner that opens a category
 * or product is only shown when that target can be ordered at the customer's location.
 */
@RestController
@RequestMapping("/api/customer/banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerBannerController {

    private final CustomerBannerService customerBannerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerBannerDto>>> getBanners(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        GeoPoint point = GeoPoint.of(latitude, longitude);
        return ResponseEntity.ok(ApiResponse.success(
                customerBannerService.getBanners(SecurityUtils.getCurrentUserId(), point.latitude(), point.longitude()),
                "Hero banners retrieved successfully"));
    }
}
