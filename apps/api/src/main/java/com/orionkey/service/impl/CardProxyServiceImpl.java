package com.orionkey.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.service.CardProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardProxyServiceImpl implements CardProxyService {

    private static final Map<Integer, String> STATUS_MESSAGE_MAP = Map.ofEntries(
            Map.entry(400, "请求参数有误"),
            Map.entry(401, "外部卡服务鉴权失败"),
            Map.entry(403, "当前服务端无权限调用外部销卡接口"),
            Map.entry(404, "卡密不存在或已过期"),
            Map.entry(409, "卡密已使用或卡片状态冲突"),
            Map.entry(422, "请求参数不符合接口要求"),
            Map.entry(429, "请求过于频繁，请稍后重试"),
            Map.entry(500, "外部卡服务内部错误"),
            Map.entry(502, "外部卡服务暂时不可用"),
            Map.entry(504, "外部卡服务请求超时")
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${card.api-base-url:https://example.com/card-api}")
    private String apiBaseUrl;

    @Value("${card.api-key:}")
    private String apiKey;

    @Override
    public Map<String, Object> cancelCardByCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "卡密不能为空");
        }

        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "未配置 CARD_API_KEY，无法调用外部销卡接口", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey.trim());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("code", code.trim()), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    buildUrl("/api/external/cards/cancel"),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return parseSuccessResponse(response.getBody(), response.getStatusCode().value());
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    resolveErrorMessage(ex.getResponseBodyAsString(), ex.getStatusCode().value()),
                    resolveHttpStatus(ex.getStatusCode().value())
            );
        } catch (ResourceAccessException ex) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "外部卡服务暂时不可用，请稍后重试", HttpStatus.BAD_GATEWAY);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "外部卡服务响应解析失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private Map<String, Object> parseSuccessResponse(String body, int statusCode) throws Exception {
        JsonNode root = readJson(body);
        if (root == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, resolveStatusMessage(statusCode), HttpStatus.BAD_GATEWAY);
        }

        if (!root.path("success").asBoolean(false)) {
            String message = firstNonBlank(
                    textOrNull(root.get("error")),
                    textOrNull(root.get("message")),
                    resolveStatusMessage(statusCode)
            );
            throw new BusinessException(ErrorCode.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
        }

        JsonNode data = root.get("data");
        if (data == null || data.isNull() || data.isMissingNode()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "外部卡服务未返回有效数据", HttpStatus.BAD_GATEWAY);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("card_id", data.hasNonNull("cardId") ? data.get("cardId").asLong() : null);
        result.put("code", textOrNull(data.get("code")));
        result.put("status", textOrNull(data.get("status")));
        result.put("refund_amount", decimalOrNull(data.get("refundAmount")));
        result.put("cancelled_at", textOrNull(data.get("cancelledAt")));
        return result;
    }

    private JsonNode readJson(String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return objectMapper.readTree(body);
    }

    private String resolveErrorMessage(String body, int statusCode) {
        try {
            JsonNode root = readJson(body);
            if (root != null) {
                return firstNonBlank(
                        textOrNull(root.get("error")),
                        textOrNull(root.get("message")),
                        resolveStatusMessage(statusCode)
                );
            }
        } catch (Exception ignored) {
        }

        if (StringUtils.hasText(body)) {
            return body.trim();
        }
        return resolveStatusMessage(statusCode);
    }

    private String resolveStatusMessage(int statusCode) {
        return STATUS_MESSAGE_MAP.getOrDefault(statusCode, "外部卡服务返回错误 " + statusCode);
    }

    private HttpStatus resolveHttpStatus(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    private String buildUrl(String path) {
        String base = StringUtils.trimTrailingCharacter(apiBaseUrl.trim(), '/');
        return base + path;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "请求失败";
    }
}
