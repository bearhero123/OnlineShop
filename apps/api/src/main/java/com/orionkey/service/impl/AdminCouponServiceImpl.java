package com.orionkey.service.impl;

import com.orionkey.constant.CouponDiscountType;
import com.orionkey.constant.CouponStatus;
import com.orionkey.constant.ErrorCode;
import com.orionkey.entity.CouponCode;
import com.orionkey.entity.Product;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.CouponCodeRepository;
import com.orionkey.repository.ProductRepository;
import com.orionkey.service.AdminCouponService;
import com.orionkey.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCouponServiceImpl implements AdminCouponService {

    private final CouponCodeRepository couponCodeRepository;
    private final ProductRepository productRepository;
    private final CouponService couponService;

    @Override
    public List<?> listCoupons() {
        List<CouponCode> coupons = couponCodeRepository.findByIsDeletedOrderByCreatedAtDesc(0);
        Map<UUID, Product> applicableProductMap = loadApplicableProductMap(coupons);
        return coupons.stream()
                .map(coupon -> toMap(coupon, applicableProductMap))
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
        coupon.setMaxUses(parseMaxUses(request.get("max_uses")));
        coupon.setUsedCount(0);
        coupon.setEnabled(parseBoolean(request.get("is_enabled"), true));
        coupon.setRemark(trimToNull((String) request.get("remark")));
        coupon.setApplicableProductIds(parseApplicableProductIds(request.get("product_ids")));
        validateDefinition(coupon);
        couponCodeRepository.save(coupon);
    }

    @Override
    @Transactional
    public void updateCoupon(UUID id, Map<String, Object> request) {
        CouponCode coupon = couponCodeRepository.findById(id)
                .filter(item -> item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠码不存在"));

        if (isDefinitionLocked(coupon) && changesDefinition(request, coupon)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已使用或占用中的优惠码不允许修改核心规则");
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
        if (request.containsKey("max_uses")) {
            coupon.setMaxUses(parseMaxUses(request.get("max_uses")));
        }
        if (request.containsKey("is_enabled")) {
            coupon.setEnabled(parseBoolean(request.get("is_enabled"), true));
        }
        if (request.containsKey("remark")) {
            coupon.setRemark(trimToNull((String) request.get("remark")));
        }
        if (request.containsKey("product_ids")) {
            coupon.setApplicableProductIds(parseApplicableProductIds(request.get("product_ids")));
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
        if (coupon.getStatus() != CouponStatus.AVAILABLE || effectiveUsedCount(coupon) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅可删除从未使用且当前未占用的优惠码");
        }
        coupon.setIsDeleted(1);
        couponCodeRepository.save(coupon);
    }

    private Map<String, Object> toMap(CouponCode coupon, Map<UUID, Product> applicableProductMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", coupon.getId());
        map.put("code", coupon.getCode());
        map.put("name", coupon.getName());
        map.put("discount_type", coupon.getDiscountType().name());
        map.put("discount_value", coupon.getDiscountValue());
        map.put("max_uses", effectiveMaxUses(coupon));
        map.put("used_count", effectiveUsedCount(coupon));
        map.put("remaining_uses", Math.max(0, effectiveMaxUses(coupon) - effectiveUsedCount(coupon)));
        map.put("status", coupon.getStatus().name());
        map.put("is_enabled", coupon.isEnabled());
        map.put("remark", coupon.getRemark());
        map.put("reserved_order_id", coupon.getReservedOrderId());
        map.put("reserved_at", coupon.getReservedAt());
        map.put("used_order_id", coupon.getUsedOrderId());
        map.put("used_at", coupon.getUsedAt());
        map.put("applicable_products", buildApplicableProducts(coupon, applicableProductMap));
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
        if (request.containsKey("max_uses")) {
            int newMaxUses = parseMaxUses(request.get("max_uses"));
            if (newMaxUses != effectiveMaxUses(coupon)) {
                return true;
            }
        }
        if (request.containsKey("product_ids")) {
            String newApplicableProductIds = parseApplicableProductIds(request.get("product_ids"));
            if (!Objects.equals(normalizeApplicableProductIds(coupon.getApplicableProductIds()), newApplicableProductIds)) {
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
        if (effectiveMaxUses(coupon) < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可使用次数必须大于 0");
        }
        if (effectiveUsedCount(coupon) > effectiveMaxUses(coupon)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可使用次数不能小于已使用次数");
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

    private String parseApplicableProductIds(Object value) {
        List<UUID> productIds = parseApplicableProductIdList(value);
        if (productIds.isEmpty()) {
            return null;
        }
        return productIds.stream()
                .map(UUID::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private List<UUID> parseApplicableProductIdList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "适用商品格式不正确");
        }

        LinkedHashSet<UUID> productIds = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }

            UUID productId;
            if (item instanceof UUID uuid) {
                productId = uuid;
            } else {
                try {
                    productId = UUID.fromString(item.toString().trim());
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "适用商品格式不正确");
                }
            }
            productIds.add(productId);
        }

        if (productIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size() || products.stream().anyMatch(product -> product.getIsDeleted() != 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "适用商品中存在无效商品");
        }
        return List.copyOf(productIds);
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

    private int parseMaxUses(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可使用次数不能为空");
        }
        try {
            int parsed = Integer.parseInt(value.toString().trim());
            if (parsed < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "可使用次数必须大于 0");
            }
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可使用次数必须为正整数");
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

    private String normalizeApplicableProductIds(String rawApplicableProductIds) {
        if (rawApplicableProductIds == null || rawApplicableProductIds.isBlank()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : rawApplicableProductIds.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized);
    }

    private List<UUID> parseStoredApplicableProductIds(String rawApplicableProductIds) {
        if (rawApplicableProductIds == null || rawApplicableProductIds.isBlank()) {
            return List.of();
        }

        LinkedHashSet<UUID> productIds = new LinkedHashSet<>();
        for (String token : rawApplicableProductIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                productIds.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 历史脏数据仅影响展示，不在这里放大失败范围。
            }
        }
        return List.copyOf(productIds);
    }

    private Map<UUID, Product> loadApplicableProductMap(List<CouponCode> coupons) {
        LinkedHashSet<UUID> productIds = new LinkedHashSet<>();
        for (CouponCode coupon : coupons) {
            productIds.addAll(parseStoredApplicableProductIds(coupon.getApplicableProductIds()));
        }

        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Product> productMap = new LinkedHashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            productMap.put(product.getId(), product);
        }
        return productMap;
    }

    private List<Map<String, Object>> buildApplicableProducts(CouponCode coupon, Map<UUID, Product> applicableProductMap) {
        return parseStoredApplicableProductIds(coupon.getApplicableProductIds()).stream()
                .map(productId -> {
                    Product product = applicableProductMap.get(productId);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", productId);
                    item.put("title", product != null ? product.getTitle() : "商品已删除");
                    return item;
                })
                .toList();
    }

    private boolean isDefinitionLocked(CouponCode coupon) {
        return coupon.getStatus() != CouponStatus.AVAILABLE || effectiveUsedCount(coupon) > 0;
    }

    private int effectiveMaxUses(CouponCode coupon) {
        Integer maxUses = coupon.getMaxUses();
        return maxUses == null || maxUses < 1 ? 1 : maxUses;
    }

    private int effectiveUsedCount(CouponCode coupon) {
        Integer usedCount = coupon.getUsedCount();
        if (usedCount != null) {
            return Math.max(0, usedCount);
        }
        return coupon.getStatus() == CouponStatus.USED || coupon.getUsedOrderId() != null || coupon.getUsedAt() != null ? 1 : 0;
    }
}
