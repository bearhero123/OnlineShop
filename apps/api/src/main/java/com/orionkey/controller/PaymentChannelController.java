package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.entity.PaymentChannel;
import com.orionkey.repository.PaymentChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payment-channels")
@RequiredArgsConstructor
public class PaymentChannelController {

    private static final java.util.Set<String> SUPPORTED_PROVIDER_TYPES = java.util.Set.of(
            "epay", "native_alipay", "native_wxpay"
    );

    private final PaymentChannelRepository paymentChannelRepository;

    @GetMapping
    public ApiResponse<?> listEnabledChannels() {
        List<Map<String, Object>> channels = paymentChannelRepository
                .findByEnabledAndIsDeletedOrderBySortOrderAsc(true, 0)
                .stream()
                .filter(channel -> channel.getProviderType() != null
                        && SUPPORTED_PROVIDER_TYPES.contains(channel.getProviderType().toLowerCase(java.util.Locale.ROOT)))
                .map(this::toPublicMap)
                .toList();
        return ApiResponse.success(channels);
    }

    private Map<String, Object> toPublicMap(PaymentChannel c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("channel_code", c.getChannelCode());
        map.put("channel_name", c.getChannelName());
        map.put("is_enabled", c.isEnabled());
        map.put("sort_order", c.getSortOrder());
        map.put("created_at", c.getCreatedAt());
        return map;
    }
}
