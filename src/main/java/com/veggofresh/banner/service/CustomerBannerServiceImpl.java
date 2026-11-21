package com.veggofresh.banner.service;

import com.veggofresh.banner.dto.CustomerBannerDto;
import com.veggofresh.banner.entity.Banner;
import com.veggofresh.banner.entity.BannerActionType;
import com.veggofresh.banner.repository.BannerRepository;
import com.veggofresh.coupon.service.CouponCheckoutService;
import com.veggofresh.platform.common.GeoPoint;
import com.veggofresh.vendor.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerBannerServiceImpl implements CustomerBannerService {

    private final BannerRepository bannerRepository;
    private final CouponCheckoutService couponCheckoutService;
    private final ProductCatalogService productCatalogService;

    Instant now() {
        return Instant.now();
    }

    @Override
    public List<CustomerBannerDto> getBanners(UUID userId, double latitude, double longitude) {
        return build(userId, latitude, longitude);
    }

    @Override
    public List<CustomerBannerDto> getGuestBanners(double latitude, double longitude) {
        return build(null, latitude, longitude);
    }

    /** {@code userId == null} means a guest: no coupon can be verified, so coupon banners are skipped. */
    private List<CustomerBannerDto> build(UUID userId, double latitude, double longitude) {
        GeoPoint.of(latitude, longitude);
        List<Banner> candidates = bannerRepository.findInSchedule(now());
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> codes = new HashSet<>();
        Set<UUID> categoryIds = new HashSet<>();
        Set<UUID> productIds = new HashSet<>();
        for (Banner b : candidates) {
            if (b.getCouponCode() != null) {
                codes.add(b.getCouponCode());
            }
            if (b.getActionType() == BannerActionType.CATEGORY && b.getActionTargetId() != null) {
                categoryIds.add(b.getActionTargetId());
            } else if (b.getActionType() == BannerActionType.PRODUCT && b.getActionTargetId() != null) {
                productIds.add(b.getActionTargetId());
            }
        }
        // A banner never shows a coupon this customer cannot use (gone, off, expired, used up, or not for them).
        Set<String> usable = (userId == null || codes.isEmpty()) ? Set.of() : couponCheckoutService.usableCodes(userId, codes);
        Set<UUID> liveCategories = categoryIds.isEmpty() ? Set.of() : new HashSet<>(bannerRepository.findActiveCategoryIds(categoryIds));
        Set<UUID> liveProducts = productIds.isEmpty() ? Set.of() : new HashSet<>(bannerRepository.findActiveProductIds(productIds));

        // A banner never opens something that cannot be ordered at the customer's location.
        Set<UUID> orderableCategories = categoryIds.isEmpty() ? Set.of()
                : productCatalogService.getOrderableCategoryIds(latitude, longitude);
        Set<UUID> orderableProducts = productIds.isEmpty() ? Set.of()
                : productCatalogService.getOrderableProductIds(latitude, longitude);

        List<CustomerBannerDto> out = new ArrayList<>();
        for (Banner b : candidates) {
            if (b.getCouponCode() != null && !usable.contains(b.getCouponCode())) {
                continue;
            }
            if (b.getActionType() == BannerActionType.CATEGORY
                    && (!liveCategories.contains(b.getActionTargetId())
                    || !orderableCategories.contains(b.getActionTargetId()))) {
                continue;
            }
            if (b.getActionType() == BannerActionType.PRODUCT
                    && (!liveProducts.contains(b.getActionTargetId())
                    || !orderableProducts.contains(b.getActionTargetId()))) {
                continue;
            }
            out.add(CustomerBannerDto.builder()
                    .id(b.getId())
                    .title(b.getTitle())
                    .subtitle(b.getSubtitle())
                    .imageUrl(b.getImageUrl())
                    .couponCode(b.getCouponCode())
                    .actionType(b.getActionType())
                    .actionTargetId(b.getActionTargetId())
                    .build());
        }
        return out;
    }
}
