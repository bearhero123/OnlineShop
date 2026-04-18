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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponCodeRepository couponCodeRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CouponApplication previewCoupon(String couponCode, List<CouponOrderLine> orderLines) {
        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);
        CouponAmounts amounts = calculateAmounts(coupon, orderLines);
        validateCouponAvailable(coupon, amounts.totalAmount(), amounts.eligibleAmount(), false, null);
        return buildApplication(coupon, amounts.totalAmount(), amounts.eligibleAmount());
    }

    @Override
    @Transactional
    public CouponApplication reserveCoupon(String couponCode, UUID orderId, List<CouponOrderLine> orderLines) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));
        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);
        CouponAmounts amounts = calculateAmounts(coupon, orderLines);
        validateCouponAvailable(coupon, amounts.totalAmount(), amounts.eligibleAmount(), true, order.getId());

        CouponApplication application = buildApplication(coupon, amounts.totalAmount(), amounts.eligibleAmount());
        order.setCouponReserved(true);
        order.setCouponUsageCounted(false);
        orderRepository.save(order);
        coupon.setStatus(CouponStatus.LOCKED);
        coupon.setReservedOrderId(orderId);
        coupon.setReservedAt(LocalDateTime.now());
        couponCodeRepository.save(coupon);
        return application;
    }

    @Override
    @Transactional
    public void markCouponUsed(Order order) {
        Order lockedOrder = orderRepository.findByIdForUpdate(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));

        String couponCode = normalizeCode(lockedOrder.getCouponCode());
        if (couponCode == null) {
            return;
        }

        CouponCode coupon = loadCouponForUpdate(couponCode);
        syncCouponState(coupon);

        if (isCouponUsageCounted(lockedOrder)) {
            return;
        }

        if (coupon.getUsedOrderId() != null
                && coupon.getUsedOrderId().equals(lockedOrder.getId())
                && isCouponExhausted(coupon)) {
            lockedOrder.setCouponReserved(false);
            lockedOrder.setCouponUsageCounted(true);
            orderRepository.save(lockedOrder);
            return;
        }

        if (isCouponReserved(lockedOrder) || isLegacyReservedForOrder(coupon, lockedOrder.getId())) {
            consumeCoupon(coupon, lockedOrder);
            return;
        }

        if (!coupon.isEnabled()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已停用，无法继续使用");
        }
        if (isCouponExhausted(coupon)) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已达到使用上限");
        }
        consumeCoupon(coupon, lockedOrder);
    }

    @Override
    @Transactional
    public void releaseCouponReservation(Order order) {
        Order lockedOrder = orderRepository.findByIdForUpdate(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));

        String couponCode = normalizeCode(lockedOrder.getCouponCode());
        if (couponCode == null) {
            return;
        }

        CouponCode coupon = couponCodeRepository.findByCodeForUpdate(couponCode).orElse(null);
        if (coupon == null) {
            return;
        }
        if (isCouponReserved(lockedOrder) || isLegacyReservedForOrder(coupon, lockedOrder.getId())) {
            releaseCoupon(coupon, lockedOrder);
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

    private void validateCouponAvailable(CouponCode coupon, BigDecimal totalAmount, BigDecimal eligibleAmount,
                                         boolean reserveMode, UUID orderId) {
        if (!coupon.isEnabled()) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "优惠码无效或已失效");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单金额异常，无法使用优惠码");
        }
        if (eligibleAmount == null || eligibleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "优惠码不适用于当前商品");
        }
        if (isCouponExhausted(coupon)) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码已达到使用上限");
        }

        int remainingUses = getRemainingUses(coupon);
        long activeReservations = reserveMode
                ? countActiveReservations(coupon, orderId)
                : countActiveReservations(coupon, null);
        if (activeReservations >= remainingUses) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠码暂时被其他订单占用");
        }
    }

    private CouponApplication buildApplication(CouponCode coupon, BigDecimal totalAmount, BigDecimal eligibleAmount) {
        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedEligible = eligibleAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount;
        if (coupon.getDiscountType() == CouponDiscountType.FIXED) {
            discountAmount = coupon.getDiscountValue().min(normalizedEligible);
        } else {
            BigDecimal eligibleActual = normalizedEligible.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            discountAmount = normalizedEligible.subtract(eligibleActual);
        }
        discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = normalizedTotal.subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
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

    private CouponAmounts calculateAmounts(CouponCode coupon, List<CouponOrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单金额异常，无法使用优惠码");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CouponOrderLine line : orderLines) {
            if (line == null || line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "订单金额异常，无法使用优惠码");
            }
            totalAmount = totalAmount.add(line.amount());
        }

        String rawApplicableProductIds = coupon.getApplicableProductIds();
        if (rawApplicableProductIds == null || rawApplicableProductIds.isBlank()) {
            return new CouponAmounts(totalAmount, totalAmount);
        }

        Set<UUID> applicableProductIds = parseApplicableProductIds(rawApplicableProductIds);
        BigDecimal eligibleAmount = BigDecimal.ZERO;
        for (CouponOrderLine line : orderLines) {
            if (line.productId() != null && applicableProductIds.contains(line.productId())) {
                eligibleAmount = eligibleAmount.add(line.amount());
            }
        }
        return new CouponAmounts(totalAmount, eligibleAmount);
    }

    private Set<UUID> parseApplicableProductIds(String rawApplicableProductIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (rawApplicableProductIds == null || rawApplicableProductIds.isBlank()) {
            return ids;
        }
        for (String token : rawApplicableProductIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 忽略历史脏数据，最终会表现为没有匹配商品，避免把优惠错误放大到全部商品。
            }
        }
        return ids;
    }

    private void syncCouponState(CouponCode coupon) {
        boolean changed = normalizeCouponCounters(coupon);
        if (coupon.getStatus() == CouponStatus.LOCKED && coupon.getReservedOrderId() != null) {
            Order order = orderRepository.findById(coupon.getReservedOrderId()).orElse(null);
            if (order == null) {
                releaseCoupon(coupon, null);
                return;
            }

            if ((order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.DELIVERED)
                    && (isCouponReserved(order) || isLegacyReservedForOrder(coupon, order.getId()))
                    && !isCouponUsageCounted(order)) {
                consumeCoupon(coupon, order);
                return;
            }

            if (order.getStatus() == OrderStatus.EXPIRED
                    || (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now()))) {
                releaseCoupon(coupon, order);
                return;
            }
        }
        if (refreshCouponSummary(coupon) || changed) {
            couponCodeRepository.save(coupon);
        }
    }

    private void consumeCoupon(CouponCode coupon, Order order) {
        normalizeCouponCounters(coupon);
        coupon.setUsedCount(getUsedCount(coupon) + 1);
        coupon.setUsedOrderId(order.getId());
        coupon.setUsedAt(LocalDateTime.now());
        order.setCouponReserved(false);
        order.setCouponUsageCounted(true);
        orderRepository.save(order);
        refreshCouponSummary(coupon);
        couponCodeRepository.save(coupon);
    }

    private void releaseCoupon(CouponCode coupon, Order order) {
        if (order != null) {
            order.setCouponReserved(false);
            orderRepository.save(order);
        }
        refreshCouponSummary(coupon);
        couponCodeRepository.save(coupon);
    }

    private boolean refreshCouponSummary(CouponCode coupon) {
        boolean changed = normalizeCouponCounters(coupon);

        if (isCouponExhausted(coupon)) {
            changed |= coupon.getStatus() != CouponStatus.USED;
            changed |= coupon.getReservedOrderId() != null;
            changed |= coupon.getReservedAt() != null;
            coupon.setStatus(CouponStatus.USED);
            coupon.setReservedOrderId(null);
            coupon.setReservedAt(null);
            return changed;
        }

        if (countActiveReservations(coupon, null) > 0) {
            changed |= coupon.getStatus() != CouponStatus.LOCKED;
            coupon.setStatus(CouponStatus.LOCKED);
            if (!isLegacyTrackedReservationActive(coupon) && !isTrackedReservationActive(coupon)) {
                changed |= coupon.getReservedOrderId() != null;
                changed |= coupon.getReservedAt() != null;
                coupon.setReservedOrderId(null);
                coupon.setReservedAt(null);
            }
            return changed;
        }

        changed |= coupon.getStatus() != CouponStatus.AVAILABLE;
        changed |= coupon.getReservedOrderId() != null;
        changed |= coupon.getReservedAt() != null;
        coupon.setStatus(CouponStatus.AVAILABLE);
        coupon.setReservedOrderId(null);
        coupon.setReservedAt(null);
        return changed;
    }

    private long countActiveReservations(CouponCode coupon, UUID excludeOrderId) {
        LocalDateTime now = LocalDateTime.now();
        long count = excludeOrderId == null
                ? orderRepository.countActiveCouponReservations(coupon.getCode(), now)
                : orderRepository.countActiveCouponReservationsExcludingOrder(coupon.getCode(), excludeOrderId, now);
        if (count > 0) {
            return count;
        }
        return hasLegacyActiveReservation(coupon, excludeOrderId, now) ? 1 : 0;
    }

    private boolean hasLegacyActiveReservation(CouponCode coupon, UUID excludeOrderId, LocalDateTime now) {
        if (coupon.getStatus() != CouponStatus.LOCKED || coupon.getReservedOrderId() == null) {
            return false;
        }
        if (excludeOrderId != null && excludeOrderId.equals(coupon.getReservedOrderId())) {
            return false;
        }
        Order order = orderRepository.findById(coupon.getReservedOrderId()).orElse(null);
        if (order == null || isCouponReserved(order) || isCouponUsageCounted(order)) {
            return false;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(now)) {
            return false;
        }
        return coupon.getCode().equals(normalizeCode(order.getCouponCode()));
    }

    private boolean isLegacyReservedForOrder(CouponCode coupon, UUID orderId) {
        return coupon.getStatus() == CouponStatus.LOCKED
                && coupon.getReservedOrderId() != null
                && coupon.getReservedOrderId().equals(orderId);
    }

    private boolean isTrackedReservationActive(CouponCode coupon) {
        if (coupon.getReservedOrderId() == null) {
            return false;
        }
        Order order = orderRepository.findById(coupon.getReservedOrderId()).orElse(null);
        return order != null
                && isCouponReserved(order)
                && order.getStatus() == OrderStatus.PENDING
                && (order.getExpiresAt() == null || order.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private boolean isLegacyTrackedReservationActive(CouponCode coupon) {
        return hasLegacyActiveReservation(coupon, null, LocalDateTime.now());
    }

    private boolean normalizeCouponCounters(CouponCode coupon) {
        boolean changed = false;
        if (coupon.getMaxUses() == null || coupon.getMaxUses() < 1) {
            coupon.setMaxUses(1);
            changed = true;
        }
        if (coupon.getUsedCount() == null) {
            coupon.setUsedCount(coupon.getStatus() == CouponStatus.USED
                    || coupon.getUsedOrderId() != null
                    || coupon.getUsedAt() != null ? 1 : 0);
            changed = true;
        } else if (coupon.getUsedCount() < 0) {
            coupon.setUsedCount(0);
            changed = true;
        }
        return changed;
    }

    private boolean isCouponExhausted(CouponCode coupon) {
        return getUsedCount(coupon) >= getMaxUses(coupon);
    }

    private int getRemainingUses(CouponCode coupon) {
        return Math.max(0, getMaxUses(coupon) - getUsedCount(coupon));
    }

    private int getMaxUses(CouponCode coupon) {
        Integer maxUses = coupon.getMaxUses();
        return maxUses == null || maxUses < 1 ? 1 : maxUses;
    }

    private int getUsedCount(CouponCode coupon) {
        Integer usedCount = coupon.getUsedCount();
        if (usedCount != null) {
            return Math.max(0, usedCount);
        }
        return coupon.getStatus() == CouponStatus.USED || coupon.getUsedOrderId() != null || coupon.getUsedAt() != null ? 1 : 0;
    }

    private boolean isCouponReserved(Order order) {
        return Boolean.TRUE.equals(order.getCouponReserved());
    }

    private boolean isCouponUsageCounted(Order order) {
        return Boolean.TRUE.equals(order.getCouponUsageCounted());
    }

    private record CouponAmounts(
            BigDecimal totalAmount,
            BigDecimal eligibleAmount
    ) {}
}
