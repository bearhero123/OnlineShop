package com.orionkey.service;

import com.orionkey.entity.Order;

import java.math.BigDecimal;
import java.util.UUID;

public interface CouponService {

    CouponApplication previewCoupon(String couponCode, BigDecimal totalAmount);

    CouponApplication reserveCoupon(String couponCode, UUID orderId, BigDecimal totalAmount);

    void markCouponUsed(Order order);

    void releaseCouponReservation(Order order);

    String normalizeCode(String couponCode);

    record CouponApplication(
            String couponCode,
            String discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            BigDecimal actualAmount
    ) {}
}
