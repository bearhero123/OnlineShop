package com.orionkey.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AdminCouponService {

    List<?> listCoupons();

    void createCoupon(Map<String, Object> request);

    void updateCoupon(UUID id, Map<String, Object> request);

    void deleteCoupon(UUID id);
}
