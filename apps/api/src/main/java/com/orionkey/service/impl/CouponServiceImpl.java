package com.orionkey.service.impl;

import com.orionkey.constant.CouponDiscountType;
import com.orionkey.constant.CouponStatus;
import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.CouponCode;
import com.orionkey.entity.Order;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.CouponCodeRepository;
import com.orionkey.repository.OrderRepository;
import com.orionkey.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponCodeRepository couponCodeRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CouponApplication previewCoupon(String couponCode, BigDecimal totalAmount) {
        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);
        validateCouponAvailable(coupon, totalAmount);
        return buildApplication(coupon, totalAmount);
    }

    @Override
    @Transactional
    public CouponApplication reserveCoupon(String couponCode, UUID orderId, BigDecimal totalAmount) {
        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);
        validateCouponAvailable(coupon, totalAmount);

        CouponApplication application = buildApplication(coupon, totalAmount);
        coupon.setStatus(CouponStatus.LOCKED);
        coupon.setReservedOrderId(orderId);
        coupon.setReservedAt(LocalDateTime.now());
        couponCodeRepository.save(coupon);
        return application;
    }

    @Override
    @Transactional
    public void markCouponUsed(Order order) {
        String couponCode = normalizeCode(order.getCouponCode());
        if (couponCode == null) {
            return;
        }

        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);

        if (coupon.getStatus() == CouponStatus.USED && order.getId().equals(coupon.getUsedOrderId())) {
            return;
        }

        if (coupon.getStatus() == CouponStatus.LOCKED && order.getId().equals(coupon.getReservedOrderId())) {
            consumeCoupon(coupon, order.getId());
            return;
        }

        if (coupon.getStatus() == CouponStatus.AVAILABLE) {
            if (!coupon.isEnabled()) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已停用，无法继续使用");
            }
            consumeCoupon(coupon, order.getId());
            return;
        }

        throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已被其他订单使用或占用");
    }

    @Override
    @Transactional
    public void releaseCouponReservation(Order order) {
        String couponCode = normalizeCode(order.getCouponCode());
        if (couponCode == null) {
            return;
        }

        CouponCode coupon = couponCodeRepository.findByCodeForUpdate(couponCode).orElse(null);
        if (coupon == null) {
            return;
        }
        if (coupon.getStatus() == CouponStatus.LOCKED && order.getId().equals(coupon.getReservedOrderId())) {
            releaseCoupon(coupon);
        }
    }

    @Override
    public String normalizeCode(String couponCode) {
        if (couponCode == null) {
            return null;
        }
        String normalized = couponCode.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private CouponCode loadCouponForUpdate(String couponCode) {
        String normalized = normalizeCode(couponCode);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "请输入优惠码");
        }
        return couponCodeRepository.findByCodeForUpdate(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_INVALID, "优惠码无效或已失效"));
    }

    private void validateCouponAvailable(CouponCode coupon, BigDecimal totalAmount) {
        if (!coupon.isEnabled()) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "优惠码无效或已失效");
        }
        if (coupon.getStatus() == CouponStatus.USED) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已被使用");
        }
        if (coupon.getStatus() == CouponStatus.LOCKED) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码暂时被其他订单占用");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单金额异常，无法使用优惠码");
        }
    }

    private CouponApplication buildApplication(CouponCode coupon, BigDecimal totalAmount) {
        BigDecimal actualAmount;
        if (coupon.getDiscountType() == CouponDiscountType.FIXED) {
            actualAmount = totalAmount.subtract(coupon.getDiscountValue()).max(BigDecimal.ZERO);
        } else {
            actualAmount = totalAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        actualAmount = actualAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = totalAmount.subtract(actualAmount).setScale(2, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "优惠码未产生有效优惠，请检查配置");
        }
        return new CouponApplication(
                coupon.getCode(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                discountAmount,
                actualAmount
        );
    }

    private void syncCouponState(CouponCode coupon) {
        if (coupon.getStatus() != CouponStatus.LOCKED || coupon.getReservedOrderId() == null) {
            return;
        }

        Order order = orderRepository.findById(coupon.getReservedOrderId()).orElse(null);
        if (order == null) {
            releaseCoupon(coupon);
            return;
        }

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.DELIVERED) {
            consumeCoupon(coupon, order.getId());
            return;
        }

        if (order.getStatus() == OrderStatus.EXPIRED
                || (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now()))) {
            releaseCoupon(coupon);
        }
    }

    private void consumeCoupon(CouponCode coupon, UUID orderId) {
        coupon.setStatus(CouponStatus.USED);
        coupon.setUsedOrderId(orderId);
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setReservedOrderId(null);
        coupon.setReservedAt(null);
        couponCodeRepository.save(coupon);
    }

    private void releaseCoupon(CouponCode coupon) {
        coupon.setStatus(CouponStatus.AVAILABLE);
        coupon.setReservedOrderId(null);
        coupon.setReservedAt(null);
        couponCodeRepository.save(coupon);
    }
}
