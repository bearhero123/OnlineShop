package com.orionkey.controller;

import com.orionkey.annotation.LogOperation;
import com.orionkey.common.ApiResponse;
import com.orionkey.service.AdminCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @GetMapping
    public ApiResponse<?> listCoupons() {
        return ApiResponse.success(adminCouponService.listCoupons());
    }

    @LogOperation(action = "coupon.create", targetType = "COUPON", detail = "'创建优惠码'")
    @PostMapping
    public ApiResponse<Void> createCoupon(@RequestBody Map<String, Object> request) {
        adminCouponService.createCoupon(request);
        return ApiResponse.success();
    }

    @LogOperation(action = "coupon.update", targetType = "COUPON", targetId = "#id", detail = "'修改优惠码'")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateCoupon(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        adminCouponService.updateCoupon(id, request);
        return ApiResponse.success();
    }

    @LogOperation(action = "coupon.delete", targetType = "COUPON", targetId = "#id", detail = "'删除优惠码'")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable UUID id) {
        adminCouponService.deleteCoupon(id);
        return ApiResponse.success();
    }
}
