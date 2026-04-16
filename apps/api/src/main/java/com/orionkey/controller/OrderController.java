package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import com.orionkey.service.DeliverService;
import com.orionkey.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final DeliverService deliverService;
    private final com.orionkey.service.PaymentService paymentService;

    @PostMapping
    public ApiResponse<?> createOrder(@RequestBody Map<String, Object> request,
                                      @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
                                      HttpServletRequest httpRequest) {
        return ApiResponse.success(orderService.createDirectOrder(
                request, RequestContext.getUserId(), httpRequest.getRemoteAddr(), sessionToken));
    }

    @PostMapping("/from-cart")
    public ApiResponse<?> createCartOrder(@RequestBody Map<String, Object> request,
                                          @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
                                          HttpServletRequest httpRequest) {
        return ApiResponse.success(orderService.createCartOrder(
                request, RequestContext.getUserId(), httpRequest.getRemoteAddr(), sessionToken));
    }

    @PostMapping("/preview-coupon")
    public ApiResponse<?> previewDirectCoupon(@RequestBody Map<String, Object> request,
                                              @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        return ApiResponse.success(orderService.previewDirectCoupon(
                request, RequestContext.getUserId(), sessionToken));
    }

    @PostMapping("/from-cart/preview-coupon")
    public ApiResponse<?> previewCartCoupon(@RequestBody Map<String, Object> request,
                                            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        return ApiResponse.success(orderService.previewCartCoupon(
                request, RequestContext.getUserId(), sessionToken));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<?> getOrderStatus(@PathVariable UUID id) {
        return ApiResponse.success(orderService.getOrderStatus(id));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<?> refreshOrderStatus(@PathVariable UUID id) {
        return ApiResponse.success(orderService.refreshOrderStatus(id));
    }

    @PostMapping("/query")
    public ApiResponse<?> queryOrders(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(deliverService.queryOrders(request));
    }

    @PostMapping("/deliver")
    public ApiResponse<?> deliverOrders(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(deliverService.deliverOrders(request));
    }

    @GetMapping("/{id}/export")
    public void exportCardKeys(@PathVariable UUID id, HttpServletResponse response) throws Exception {
        String content = deliverService.exportCardKeys(id);
        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=card-keys-" + id + ".txt");
        response.getWriter().write(content);
    }

    /**
     * 重新发起支付（移动端支付取消/失败后重试）
     */
    @PostMapping("/{id}/repay")
    public ApiResponse<?> repayOrder(@PathVariable UUID id,
                                     @RequestBody(required = false) Map<String, String> body) {
        String device = body != null ? body.get("device") : null;
        return ApiResponse.success(paymentService.repay(id, device, RequestContext.getUserId()));
    }
}
