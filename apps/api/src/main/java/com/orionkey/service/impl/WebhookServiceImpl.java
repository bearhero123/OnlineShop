package com.orionkey.service.impl;

import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.Order;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.entity.WebhookEvent;
import com.orionkey.repository.OrderRepository;
import com.orionkey.repository.PaymentChannelRepository;
import com.orionkey.repository.WebhookEventRepository;
import com.orionkey.service.CouponService;
import com.orionkey.service.EpayService;
import com.orionkey.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final EpayService epayService;
    private final PaymentServiceImpl paymentService;
    private final CouponService couponService;

    @Override
    @Transactional
    public String processEpayCallback(Map<String, String> params) {
        String tradeNo = params.get("trade_no");
        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String money = params.get("money");
        String sign = params.get("sign");

        log.info("Epay callback: out_trade_no={}, trade_status={}, money={}", outTradeNo, tradeStatus, money);

        String successResponse = callbackSuccessResponse(params);
        String failResponse = callbackFailResponse(params);

        UUID orderId;
        try {
            orderId = UUID.fromString(outTradeNo);
        } catch (IllegalArgumentException e) {
            log.error("Epay callback invalid out_trade_no: {}", outTradeNo);
            return failResponse;
        }

        EpayService.ChannelConfig channelConfig = resolveChannelConfig(orderId);
        if (channelConfig != null) {
            successResponse = callbackSuccessResponse(channelConfig);
            failResponse = callbackFailResponse(channelConfig);
        }

        String eventId = "epay_" + (tradeNo != null ? tradeNo : UUID.randomUUID());
        Optional<WebhookEvent> existingEvent = webhookEventRepository.findByEventId(eventId);
        if (existingEvent.isPresent()) {
            log.info("Epay callback already processed: {}", eventId);
            return successResponse;
        }

        // 签名失败不写入幂等表，避免恶意伪造回调占位 eventId。
        if (channelConfig == null || !epayService.verifySign(channelConfig, params, sign)) {
            log.error("Epay callback signature verification failed: out_trade_no={}, remote sign={}", outTradeNo, sign);
            return failResponse;
        }

        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            log.info("Epay callback non-success status: {}, skipping (not saved to idempotency table)", tradeStatus);
            return successResponse;
        }

        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setChannelCode("epay");
        event.setOrderId(orderId);
        event.setPayload(params.toString());

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            event.setProcessResult("ORDER_NOT_FOUND");
            log.warn("Epay callback order not found: {}", orderId);
            webhookEventRepository.save(event);
            return successResponse;
        }

        if (money == null || money.isBlank()) {
            log.error("Epay callback missing money parameter: out_trade_no={}", outTradeNo);
            event.setProcessResult("MISSING_AMOUNT");
            webhookEventRepository.save(event);
            return failResponse;
        }

        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(money);
        } catch (NumberFormatException e) {
            log.error("Epay callback invalid money format: {}, out_trade_no={}", money, outTradeNo);
            event.setProcessResult("INVALID_AMOUNT_FORMAT");
            webhookEventRepository.save(event);
            return failResponse;
        }

        if (order.getActualAmount().compareTo(callbackAmount) != 0) {
            log.error("Epay callback amount mismatch: order={}, callback={}", order.getActualAmount(), callbackAmount);
            event.setProcessResult("AMOUNT_MISMATCH");
            webhookEventRepository.save(event);
            return failResponse;
        }

        if (channelConfig != null) {
            EpayService.OrderQueryResult queryResult = epayService.queryOrder(channelConfig, outTradeNo);
            if (queryResult == null) {
                log.warn("Epay callback deferred: server-side order query returned null, out_trade_no={}", outTradeNo);
                return failResponse;
            }
            if (!isQueryStatusPaid(queryResult.tradeStatus())) {
                log.error("Epay callback rejected: query status={}, expected TRADE_SUCCESS/1, out_trade_no={}",
                        queryResult.tradeStatus(), outTradeNo);
                event.setProcessResult("QUERY_STATUS_MISMATCH");
                webhookEventRepository.save(event);
                return failResponse;
            }
            if (queryResult.money() != null) {
                try {
                    BigDecimal queryAmount = new BigDecimal(queryResult.money());
                    if (order.getActualAmount().compareTo(queryAmount) != 0) {
                        log.error("Epay callback rejected: query amount={}, order amount={}, out_trade_no={}",
                                queryAmount, order.getActualAmount(), outTradeNo);
                        event.setProcessResult("QUERY_AMOUNT_MISMATCH");
                        webhookEventRepository.save(event);
                        return failResponse;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Epay order query returned invalid money format: {}", queryResult.money());
                }
            }
            log.info("Epay callback server-side verification passed: out_trade_no={}, queryStatus={}",
                    outTradeNo, queryResult.tradeStatus());
        } else {
            log.warn("Epay callback: channel config incomplete, skipping server-side query verification for out_trade_no={}", outTradeNo);
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            couponService.markCouponUsed(order);
            orderRepository.save(order);
            event.setProcessResult("SUCCESS");
            log.info("Epay callback: order {} marked as PAID", orderId);
        } else {
            event.setProcessResult("SKIPPED_" + order.getStatus().name());
            log.info("Epay callback: order {} already {}", orderId, order.getStatus());
        }

        webhookEventRepository.save(event);
        return successResponse;
    }

    private boolean isQueryStatusPaid(String status) {
        return "TRADE_SUCCESS".equals(status) || "1".equals(status);
    }

    private EpayService.ChannelConfig resolveChannelConfig(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getPaymentMethod() == null) return null;

        PaymentChannel channel = paymentChannelRepository
                .findByChannelCodeAndIsDeleted(order.getPaymentMethod(), 0)
                .orElse(null);
        if (channel == null) return null;

        try {
            return paymentService.buildChannelConfig(channel);
        } catch (Exception e) {
            log.warn("Failed to build ChannelConfig for callback verification: {}", e.getMessage());
            return null;
        }
    }

    private String callbackSuccessResponse(Map<String, String> params) {
        return "RSA".equalsIgnoreCase(params.get("sign_type")) ? "success" : "SUCCESS";
    }

    private String callbackFailResponse(Map<String, String> params) {
        return "RSA".equalsIgnoreCase(params.get("sign_type")) ? "fail" : "FAIL";
    }

    private String callbackSuccessResponse(EpayService.ChannelConfig config) {
        return config.isRsaMode() ? "success" : "SUCCESS";
    }

    private String callbackFailResponse(EpayService.ChannelConfig config) {
        return config.isRsaMode() ? "fail" : "FAIL";
    }
}
