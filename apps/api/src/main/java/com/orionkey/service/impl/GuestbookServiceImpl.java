package com.orionkey.service.impl;

import com.orionkey.common.PageResult;
import com.orionkey.constant.ErrorCode;
import com.orionkey.constant.OrderStatus;
import com.orionkey.entity.GuestbookMessage;
import com.orionkey.entity.Order;
import com.orionkey.entity.OrderItem;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.GuestbookMessageRepository;
import com.orionkey.repository.OrderItemRepository;
import com.orionkey.repository.OrderRepository;
import com.orionkey.service.GuestbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuestbookServiceImpl implements GuestbookService {

    private static final int DEFAULT_PUBLIC_PAGE_SIZE = 20;
    private static final int DEFAULT_ADMIN_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GuestbookMessageRepository guestbookMessageRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<?> listPublicMessages(int page, int pageSize) {
        PageRequest pageable = PageRequest.of(normalizePage(page) - 1,
                normalizePageSize(pageSize, DEFAULT_PUBLIC_PAGE_SIZE));
        Page<GuestbookMessage> messagePage = guestbookMessageRepository
                .findByVisibleTrueOrderByCreatedAtDesc(pageable);
        return PageResult.of(messagePage, messagePage.getContent().stream()
                .map(this::toPublicMap)
                .toList());
    }

    @Override
    @Transactional
    public Object createMessage(Map<String, Object> request) {
        UUID orderId = parseOrderId(request.get("order_id"));
        String content = requireContent(request.get("content"));
        String nickname = normalizeNickname(request.get("nickname"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已购买成功的订单可以留言");
        }

        GuestbookMessage message = new GuestbookMessage();
        message.setOrderId(orderId);
        message.setNickname(nickname);
        message.setContent(content);
        message.setProductSummary(buildProductSummary(orderId));
        message.setVisible(true);
        guestbookMessageRepository.save(message);
        return toPublicMap(message);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<?> listAdminMessages(int page, int pageSize) {
        PageRequest pageable = PageRequest.of(normalizePage(page) - 1,
                normalizePageSize(pageSize, DEFAULT_ADMIN_PAGE_SIZE));
        Page<GuestbookMessage> messagePage = guestbookMessageRepository.findAllByOrderByCreatedAtDesc(pageable);

        Map<UUID, Order> orderMap = orderRepository.findByIdIn(messagePage.getContent().stream()
                        .map(GuestbookMessage::getOrderId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        return PageResult.of(messagePage, messagePage.getContent().stream()
                .map(message -> toAdminMap(message, orderMap.get(message.getOrderId())))
                .toList());
    }

    @Override
    @Transactional
    public void updateVisibility(UUID id, boolean isVisible) {
        GuestbookMessage message = guestbookMessageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "留言不存在"));
        message.setVisible(isVisible);
        guestbookMessageRepository.save(message);
    }

    private Map<String, Object> toPublicMap(GuestbookMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.getId());
        map.put("nickname", message.getNickname());
        map.put("content", message.getContent());
        map.put("product_summary", message.getProductSummary());
        map.put("masked_order_id", maskOrderId(message.getOrderId()));
        map.put("created_at", message.getCreatedAt());
        return map;
    }

    private Map<String, Object> toAdminMap(GuestbookMessage message, Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.getId());
        map.put("order_id", message.getOrderId());
        map.put("order_email", order != null ? order.getEmail() : null);
        map.put("order_status", order != null ? order.getStatus().name() : null);
        map.put("nickname", message.getNickname());
        map.put("content", message.getContent());
        map.put("product_summary", message.getProductSummary());
        map.put("is_visible", message.isVisible());
        map.put("created_at", message.getCreatedAt());
        return map;
    }

    private String buildProductSummary(UUID orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items.isEmpty()) {
            return null;
        }

        OrderItem first = items.get(0);
        String summary = first.getProductTitle();
        if (first.getSpecName() != null && !first.getSpecName().isBlank()) {
            summary += " / " + first.getSpecName();
        }
        if (items.size() == 1) {
            return first.getQuantity() > 1 ? summary + " x" + first.getQuantity() : summary;
        }
        return summary + " 等 " + items.size() + " 项";
    }

    private UUID parseOrderId(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单号不能为空");
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单号格式不正确");
        }
    }

    private String requireContent(Object value) {
        String content = value == null ? null : value.toString().trim();
        if (content == null || content.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "留言内容不能为空");
        }
        if (content.length() < 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "留言内容至少需要 5 个字");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "留言内容不能超过 500 个字");
        }
        return content;
    }

    private String normalizeNickname(Object value) {
        if (value == null) {
            return null;
        }
        String nickname = value.toString().trim();
        if (nickname.isEmpty()) {
            return null;
        }
        if (nickname.length() > 40) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能超过 40 个字符");
        }
        return nickname;
    }

    private String maskOrderId(UUID orderId) {
        String value = orderId.toString();
        if (value.length() <= 16) {
            return value;
        }
        return value.substring(0, 8) + "..." + value.substring(value.length() - 4);
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int pageSize, int defaultValue) {
        if (pageSize < 1) {
            return defaultValue;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
