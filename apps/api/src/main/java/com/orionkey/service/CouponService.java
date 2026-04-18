package com.orionkey.service;

import com.orionkey.entity.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CouponService {

    CouponApplication previewCoupon(String couponCode, List<CouponOrderLine> orderLines);

    CouponApplication reserveCoupon(String couponCode, UUID orderId, List<CouponOrderLine> orderLines);

    void markCouponUsed(Order order);

    void releaseCouponReservation(Order order);

    String normalizeCode(String couponCode);

    record CouponOrderLine(
            UUID productId,
            BigDecimal amount
    ) {}

    record CouponApplication(
            String couponCode,
            String discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            BigDecimal actualAmount
    ) {}
}
