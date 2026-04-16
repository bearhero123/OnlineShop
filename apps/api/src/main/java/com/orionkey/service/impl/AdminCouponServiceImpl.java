package com.orionkey.service.impl;

import com.orionkey.constant.CouponDiscountType;
import com.orionkey.constant.CouponStatus;
import com.orionkey.constant.ErrorCode;
import com.orionkey.entity.CouponCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.CouponCodeRepository;
import com.orionkey.service.AdminCouponService;
import com.orionkey.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCouponServiceImpl implements AdminCouponService {

    private final CouponCodeRepository couponCodeRepository;
    private final CouponService couponService;

    @Override
    public List<?> listCoupons() {
        return couponCodeRepository.findByIsDeletedOrderByCreatedAtDesc(0).stream()
                .map(this::toMap)
                .toList();
    }

    @Override
    @Transactional
    public void createCoupon(Map<String, Object> request) {
        CouponCode coupon = new CouponCode();
        coupon.setCode(requireCouponCode(request.get("code")));
        if (couponCodeRepository.findByCodeAndIsDeleted(coupon.getCode(), 0).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠码已存在");
        }
        coupon.setName(requireNonBlank((String) request.get("name"), "优惠码名称不能为空"));
        coupon.setDiscountType(parseDiscountType(request.get("discount_type")));
        coupon.setDiscountValue(parseDiscountValue(request.get("discount_value"), coupon.getDiscountType()));
        coupon.setEnabled(parseBoolean(request.get("is_enabled"), true));
        coupon.setRemark(trimToNull((String) request.get("remark")));
        validateDefinition(coupon);
        couponCodeRepository.save(coupon);
    }

    @Override
    @Transactional
    public void updateCoupon(UUID id, Map<String, Object> request) {
        CouponCode coupon = couponCodeRepository.findById(id)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠码不存在"));

        if (coupon.getStatus() != CouponStatus.AVAILABLE && changesDefinition(request, coupon)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已锁定或已使用的优惠码不允许修改核心规则");
        }

        if (request.containsKey("code")) {
            String code = requireCouponCode(request.get("code"));
            couponCodeRepository.findByCodeAndIsDeleted(code, 0)
                    .filter(existing -> !existing.getId().equals(coupon.getId()))
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠码已存在");
                    });
            coupon.setCode(code);
        }
        if (request.containsKey("name")) {
            coupon.setName(requireNonBlank((String) request.get("name"), "优惠码名称不能为空"));
        }
        if (request.containsKey("discount_type")) {
            coupon.setDiscountType(parseDiscountType(request.get("discount_type")));
        }
        if (request.containsKey("discount_value")) {
            coupon.setDiscountValue(parseDiscountValue(request.get("discount_value"), coupon.getDiscountType()));
        }
        if (request.containsKey("is_enabled")) {
            coupon.setEnabled(parseBoolean(request.get("is_enabled"), true));
        }
        if (request.containsKey("remark")) {
            coupon.setRemark(trimToNull((String) request.get("remark")));
        }
        validateDefinition(coupon);
        couponCodeRepository.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(UUID id) {
        CouponCode coupon = couponCodeRepository.findById(id)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠码不存在"));
        if (coupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅可删除未锁定且未使用的优惠码");
        }
        coupon.setIsDeleted(1);
        couponCodeRepository.save(coupon);
    }

    private Map<String, Object> toMap(CouponCode coupon) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", coupon.getId());
        map.put("code", coupon.getCode());
        map.put("name", coupon.getName());
        map.put("discount_type", coupon.getDiscountType().name());
        map.put("discount_value", coupon.getDiscountValue());
        map.put("status", coupon.getStatus().name());
        map.put("is_enabled", coupon.isEnabled());
        map.put("remark", coupon.getRemark());
        map.put("reserved_order_id", coupon.getReservedOrderId());
        map.put("reserved_at", coupon.getReservedAt());
        map.put("used_order_id", coupon.getUsedOrderId());
        map.put("used_at", coupon.getUsedAt());
        map.put("created_at", coupon.getCreatedAt());
        return map;
    }

    private boolean changesDefinition(Map<String, Object> request, CouponCode coupon) {
        if (request.containsKey("code")) {
            String newCode = couponService.normalizeCode((String) request.get("code"));
            if (newCode != null && !newCode.equals(coupon.getCode())) {
                return true;
            }
        }
        if (request.containsKey("discount_type")) {
            CouponDiscountType newType = parseDiscountType(request.get("discount_type"));
            if (newType != coupon.getDiscountType()) {
                return true;
            }
        }
        if (request.containsKey("discount_value")) {
            BigDecimal newValue = parseDiscountValue(request.get("discount_value"), coupon.getDiscountType());
            if (coupon.getDiscountValue().compareTo(newValue) != 0) {
                return true;
            }
        }
        return false;
    }

    private void validateDefinition(CouponCode coupon) {
        if (coupon.getDiscountType() == CouponDiscountType.FIXED && coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "固定金额优惠必须大于 0");
        }
        if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            if (coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0
                    || coupon.getDiscountValue().compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "折扣优惠请输入 0 到 100 之间的值，例如 90 表示 9 折");
            }
        }
    }

    private String requireCouponCode(Object value) {
        String normalized = couponService.normalizeCode(value instanceof String str ? str : null);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠码不能为空");
        }
        return normalized;
    }

    private String requireNonBlank(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private CouponDiscountType parseDiscountType(Object value) {
        if (!(value instanceof String str) || str.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠类型不能为空");
        }
        try {
            return CouponDiscountType.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的优惠类型");
        }
    }

    private BigDecimal parseDiscountValue(Object value, CouponDiscountType discountType) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠值不能为空");
        }
        try {
            BigDecimal parsed = new BigDecimal(value.toString()).stripTrailingZeros();
            return parsed.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠值格式不正确");
        }
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
