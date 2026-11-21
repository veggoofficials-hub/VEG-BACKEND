package com.veggofresh.banner.service;

import com.veggofresh.banner.dto.CustomerBannerDto;

import java.util.List;
import java.util.UUID;

public interface CustomerBannerService {

    /**
     * The banners this customer should see now at the given location: switched on, inside their schedule, in the
     * admin's order, not advertising a coupon this customer cannot use, and not opening a category / product that
     * no longer exists OR that cannot be ordered at this location.
     */
    List<CustomerBannerDto> getBanners(UUID userId, double latitude, double longitude);

    /**
     * The same for a guest (not logged in). A coupon needs an account to be checked against, so banners that
     * advertise a coupon code are left out for guests.
     */
    List<CustomerBannerDto> getGuestBanners(double latitude, double longitude);
}
