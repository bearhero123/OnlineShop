package com.orionkey.entity;

import com.orionkey.constant.CouponDiscountType;
import com.orionkey.constant.CouponStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "coupon_codes")
public class CouponCode extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponDiscountType discountType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_uses")
    private Integer maxUses = 1;

    @Column(name = "used_count")
    private Integer usedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status = CouponStatus.AVAILABLE;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    private UUID reservedOrderId;

    private LocalDateTime reservedAt;

    private UUID usedOrderId;

    private LocalDateTime usedAt;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "applicable_product_ids", columnDefinition = "TEXT")
    private String applicableProductIds;

    private int isDeleted = 0;
}
