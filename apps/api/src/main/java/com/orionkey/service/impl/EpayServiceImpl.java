package com.orionkey.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.service.EpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpayServiceImpl implements EpayService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public EpayResult createPayment(ChannelConfig config, String outTradeNo, String type, String name, BigDecimal money, String clientIp, String device) {
        return config.isRsaMode()
                ? createRsaPayment(config, outTradeNo, type, name, money, clientIp, device)
                : createLegacyPayment(config, outTradeNo, type, name, money, clientIp, device);
    }

    private EpayResult createLegacyPayment(ChannelConfig config, String outTradeNo, String type, String name, BigDecimal money, String clientIp, String device) {
        // 动态拼接 return_url：基础 URL + orderId 查询参数，使 epay 回跳到订单查询页
        String dynamicReturnUrl = buildDynamicReturnUrl(config.returnUrl(), outTradeNo);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.pid());
        params.put("type", type);
        params.put("out_trade_no", outTradeNo);
        params.put("notify_url", config.notifyUrl());
        params.put("return_url", dynamicReturnUrl);
        params.put("name", name);
        params.put("money", money.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        params.put("clientip", clientIp != null ? clientIp : "127.0.0.1");
        params.put("device", device != null && !device.isBlank() ? device : "pc");

        String sign = buildSign(config.key(), params);
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        log.info("Epay createPayment: outTradeNo={}, type={}, money={}, apiUrl={}", outTradeNo, type, money, config.apiUrl());

        // Build form-urlencoded body
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        String url = config.apiUrl() + (config.apiUrl().endsWith("/") ? "" : "/") + "mapi.php";

        // 带重试的网络调用（最多重试 2 次，间隔 1 秒）
        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Epay API retry attempt {}/{}", attempt, maxRetries);
                    Thread.sleep(1000);
                }

                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                String responseBody = response.getBody();

                if (responseBody == null || responseBody.isBlank()) {
                    log.error("Epay API returned null/empty body");
                    throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：响应为空");
                }

                log.debug("Epay API raw response: {}", responseBody);

                Map<String, Object> body;
                try {
                    body = objectMapper.readValue(responseBody, new TypeReference<>() {});
                } catch (Exception parseEx) {
                    log.error("Epay API response is not valid JSON: {}", responseBody);
                    throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：响应格式异常");
                }

                int code = intValue(body.get("code"), -1);
                String msg = body.get("msg") != null ? body.get("msg").toString() : "";
                String tradeNo = body.get("trade_no") != null ? body.get("trade_no").toString() : "";
                String payUrl = body.get("payurl") != null ? body.get("payurl").toString() : null;
                String qrcode = body.get("qrcode") != null ? body.get("qrcode").toString() : null;
                String urlscheme = body.get("urlscheme") != null ? body.get("urlscheme").toString() : null;

                log.info("Epay API response: code={}, msg={}, tradeNo={}, payUrl={}, qrcode={}", code, msg, tradeNo, payUrl, qrcode);

                if (code != 1) {
                    // 业务错误不重试
                    log.error("Epay API error: code={}, msg={}", code, msg);
                    throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：" + msg);
                }

                String resultQrcode = qrcode != null ? qrcode : urlscheme;

                // 网关未返回 payUrl 且为移动端请求时，将 qrcode（收银台页面 URL）作为 H5 跳转入口
                String effectivePayUrl = payUrl;
                if (effectivePayUrl == null && device != null && !"pc".equals(device) && resultQrcode != null) {
                    effectivePayUrl = resultQrcode;
                    log.info("Epay: gateway returned no payUrl, using qrcode URL as mobile redirect: {}", effectivePayUrl);
                }

                return new EpayResult(code, msg, tradeNo, effectivePayUrl, resultQrcode);

            } catch (BusinessException e) {
                throw e; // 业务异常直接抛出，不重试
            } catch (Exception e) {
                lastException = e;
                log.warn("Epay API attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        log.error("Epay API call failed after {} retries", maxRetries + 1, lastException);
        throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：网络超时，请重试");
    }

    private EpayResult createRsaPayment(ChannelConfig config, String outTradeNo, String type, String name, BigDecimal money, String clientIp, String device) {
        String dynamicReturnUrl = buildDynamicReturnUrl(config.returnUrl(), outTradeNo);
        String method = resolveCreateMethod(config.createMethod());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.pid());
        params.put("method", method);
        if ("web".equalsIgnoreCase(method)) {
            params.put("device", normalizeDevice(device));
        }
        params.put("type", type);
        params.put("out_trade_no", outTradeNo);
        params.put("notify_url", config.notifyUrl());
        params.put("return_url", dynamicReturnUrl);
        params.put("name", name);
        params.put("money", money.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        params.put("clientip", clientIp != null ? clientIp : "127.0.0.1");
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        String sign = buildSign(config, params);
        params.put("sign", sign);
        params.put("sign_type", "RSA");

        log.info("Epay RSA createPayment: outTradeNo={}, type={}, money={}, method={}, apiUrl={}",
                outTradeNo, type, money, method, config.apiUrl());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        String url = joinUrl(config.apiUrl(), "api/pay/create");

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：响应为空");
            }

            Map<String, Object> body;
            try {
                body = objectMapper.readValue(responseBody, new TypeReference<>() {});
            } catch (Exception parseEx) {
                log.error("Epay RSA response is not valid JSON: {}", responseBody);
                throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：响应格式异常");
            }

            Map<String, String> responseParams = stringifyMap(body);
            int code = intValue(body.get("code"), -1);
            String msg = body.get("msg") != null ? body.get("msg").toString() : "";
            if (code != 0) {
                throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：" + msg);
            }

            if (!verifySign(config, responseParams, responseParams.get("sign"))) {
                log.error("Epay RSA response signature verification failed: {}", responseBody);
                throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：网关验签失败");
            }

            String tradeNo = responseParams.get("trade_no");
            String payType = responseParams.get("pay_type");
            String payInfo = responseParams.get("pay_info");

            if (payInfo == null || payInfo.isBlank()) {
                throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：网关未返回支付链接");
            }

            String payUrl = null;
            String qrcodeUrl = null;
            switch (payType != null ? payType.toLowerCase(Locale.ROOT) : "") {
                case "jump", "urlscheme" -> payUrl = payInfo;
                case "qrcode" -> qrcodeUrl = payInfo;
                case "html" -> throw new BusinessException(
                        ErrorCode.WEBHOOK_VERIFY_FAIL,
                        "支付创建失败：网关返回 HTML 跳转页，请在渠道配置中将 create_method 设为 jump"
                );
                default -> payUrl = payInfo;
            }

            return new EpayResult(code, msg, tradeNo, payUrl, qrcodeUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Epay RSA createPayment failed: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.WEBHOOK_VERIFY_FAIL, "支付创建失败：网络超时，请重试");
        }
    }

    @Override
    public String buildSign(ChannelConfig config, Map<String, String> params) {
        if (config.isRsaMode()) {
            return rsaPrivateSign(config.merchantPrivateKey(), getSignContent(params));
        }
        return buildSign(config.key(), params);
    }

    @Override
    public String buildSign(String merchantKey, Map<String, String> params) {
        // 1. Filter out sign, sign_type, and empty values
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("sign".equals(key) || "sign_type".equals(key)) continue;
            if (value == null || value.isEmpty()) continue;
            sorted.put(key, value);
        }

        // 2. Concatenate sorted params: a=b&c=d&e=f
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }

        // 3. Append merchant key directly
        sb.append(merchantKey);

        // 4. MD5 hash
        return md5(sb.toString());
    }

    @Override
    public OrderQueryResult queryOrder(ChannelConfig config, String outTradeNo) {
        if (config.isRsaMode()) {
            return queryRsaOrder(config, outTradeNo);
        }

        String url = config.apiUrl() + (config.apiUrl().endsWith("/") ? "" : "/")
                + "api.php?act=order&pid=" + config.pid() + "&key=" + config.key()
                + "&out_trade_no=" + outTradeNo;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("Epay order query returned empty body: outTradeNo={}", outTradeNo);
                return null;
            }
            log.debug("Epay order query response: {}", body);

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});

            // 网关返回错误码时（如订单不存在），返回 null
            if (result.containsKey("code") && intValue(result.get("code"), -1) != 1) {
                log.warn("Epay order query error: {}", body);
                return null;
            }

            String tradeStatus = result.get("status") != null ? result.get("status").toString() : null;
            String money = result.get("money") != null ? result.get("money").toString() : null;
            String tradeNo = result.get("trade_no") != null ? result.get("trade_no").toString() : null;
            return new OrderQueryResult(tradeStatus, money, tradeNo);
        } catch (Exception e) {
            log.warn("Epay order query failed: outTradeNo={}, error={}", outTradeNo, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean verifySign(ChannelConfig config, Map<String, String> params, String sign) {
        if (config.isRsaMode()) {
            if (sign == null || sign.isEmpty()) return false;
            String timestamp = params.get("timestamp");
            if (timestamp == null || timestamp.isBlank()) return false;
            try {
                long ts = Long.parseLong(timestamp);
                if (Math.abs((System.currentTimeMillis() / 1000) - ts) > 300) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
            return rsaPublicVerify(config.platformPublicKey(), getSignContent(params), sign);
        }
        return verifySign(config.key(), params, sign);
    }

    @Override
    public boolean verifySign(String merchantKey, Map<String, String> params, String sign) {
        if (sign == null || sign.isEmpty()) return false;
        String expected = buildSign(merchantKey, params);
        return expected.equalsIgnoreCase(sign);
    }

    private OrderQueryResult queryRsaOrder(ChannelConfig config, String outTradeNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.pid());
        params.put("out_trade_no", outTradeNo);
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("sign", buildSign(config, params));
        params.put("sign_type", "RSA");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(joinUrl(config.apiUrl(), "api/pay/query"), request, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("Epay RSA order query returned empty body: outTradeNo={}", outTradeNo);
                return null;
            }

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});
            Map<String, String> responseParams = stringifyMap(result);
            if (intValue(result.get("code"), -1) != 0) {
                log.warn("Epay RSA order query error: {}", body);
                return null;
            }

            if (!verifySign(config, responseParams, responseParams.get("sign"))) {
                log.warn("Epay RSA order query signature verification failed: {}", body);
                return null;
            }

            String tradeStatus = responseParams.get("status");
            String money = responseParams.get("money");
            String tradeNo = responseParams.get("trade_no");
            return new OrderQueryResult(tradeStatus, money, tradeNo);
        } catch (Exception e) {
            log.warn("Epay RSA order query failed: outTradeNo={}, error={}", outTradeNo, e.getMessage());
            return null;
        }
    }

    private String buildDynamicReturnUrl(String baseReturnUrl, String outTradeNo) {
        return baseReturnUrl + (baseReturnUrl.contains("?") ? "&" : "?") + "orderId=" + outTradeNo;
    }

    private String normalizeDevice(String device) {
        if (device == null || device.isBlank()) {
            return "pc";
        }
        return switch (device.toLowerCase(Locale.ROOT)) {
            case "pc", "mobile", "qq", "wechat", "alipay" -> device.toLowerCase(Locale.ROOT);
            default -> "pc";
        };
    }

    private String resolveCreateMethod(String configuredMethod) {
        if (configuredMethod == null || configuredMethod.isBlank()) {
            return "jump";
        }
        return configuredMethod.trim().toLowerCase(Locale.ROOT);
    }

    private String joinUrl(String baseUrl, String path) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + path;
    }

    private Map<String, String> stringifyMap(Map<String, Object> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String getSignContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("sign".equals(key) || "sign_type".equals(key)) continue;
            if (value == null || value.isBlank()) continue;
            sorted.put(key, value);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private String rsaPrivateSign(String merchantPrivateKey, String content) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(merchantPrivateKey));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new RuntimeException("RSA sign failed", e);
        }
    }

    private boolean rsaPublicVerify(String platformPublicKey, String content, String sign) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(platformPublicKey));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.warn("RSA verify failed: {}", e.getMessage());
            return false;
        }
    }

    private PrivateKey loadPrivateKey(String keyText) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(sanitizePem(keyText));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String keyText) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(sanitizePem(keyText));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String sanitizePem(String keyText) {
        return keyText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }
}
