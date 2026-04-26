package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.PaymentOrderResponse;
import com.retailshop.dto.PaymentStatusResponse;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.exception.BusinessException;
import com.retailshop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PhonePePaymentService implements PaymentService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt;

    @Override
    public PaymentOrderResponse createPaymentOrder(BigDecimal amount, String receipt) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long amountSubunits = safeAmount.movePointRight(2).longValue();
        String merchantOrderId = "PP-" + System.currentTimeMillis();

        if (!isConfigured()) {
            return PaymentOrderResponse.builder()
                    .provider("PHONEPE")
                    .configured(false)
                    .orderId(merchantOrderId)
                    .receipt(receipt)
                    .currency("INR")
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .build();
        }

        try {
            String accessToken = fetchAccessToken();
            String redirectUrl = appendMerchantOrderId(appProperties.getPhonepe().getRedirectUrl(), merchantOrderId);
            String callbackUrl = blankToNull(appProperties.getPhonepe().getWebhookUrl());
            String payload = objectMapper.writeValueAsString(Map.of(
                    "merchantOrderId", merchantOrderId,
                    "amount", amountSubunits,
                    "expireAfter", 1200,
                    "paymentFlow", Map.of(
                            "type", "PG_CHECKOUT",
                            "message", "Retail shop checkout",
                            "merchantUrls", callbackUrl == null
                                    ? Map.of("redirectUrl", redirectUrl)
                                    : Map.of("redirectUrl", redirectUrl, "callbackUrl", callbackUrl)
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(getApiBaseUrl() + "/checkout/v2/pay"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "O-Bearer " + accessToken)
                    .build();
            JsonNode body = readResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            String paymentUrl = firstText(body,
                    "redirectUrl",
                    "paymentUrl",
                    "checkoutPageUrl",
                    "data.redirectUrl",
                    "data.paymentUrl",
                    "data.instrumentResponse.redirectInfo.url",
                    "orderTokenDetails.paymentUrl");
            if (paymentUrl == null) {
                throw new BusinessException(firstText(body, "message", "error.message", "Unable to create PhonePe payment"));
            }
            return PaymentOrderResponse.builder()
                    .provider("PHONEPE")
                    .configured(true)
                    .orderId(merchantOrderId)
                    .receipt(receipt)
                    .currency("INR")
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .paymentUrl(paymentUrl)
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("Unable to create PhonePe payment");
        }
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(String merchantOrderId) {
        if (isBlank(merchantOrderId)) {
            throw new BusinessException("Merchant order id is required");
        }
        if (!isConfigured()) {
            return PaymentStatusResponse.builder()
                    .provider("PHONEPE")
                    .merchantOrderId(merchantOrderId)
                    .state("COMPLETED")
                    .paymentState("SUCCESS")
                    .paymentMode("TEST")
                    .transactionId("local-" + merchantOrderId)
                    .success(true)
                    .build();
        }

        try {
            String accessToken = fetchAccessToken();
            HttpRequest request = HttpRequest.newBuilder(URI.create(getApiBaseUrl() + "/checkout/v2/order/" + encode(merchantOrderId) + "/status"))
                    .GET()
                    .header("Accept", "application/json")
                    .header("Authorization", "O-Bearer " + accessToken)
                    .build();
            JsonNode body = readResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            String state = firstText(body, "state", "data.state", "orderStatus");
            String paymentState = firstText(body, "paymentDetails.0.state", "paymentDetails.state", "data.paymentDetails.0.state", "paymentStatus");
            String paymentMode = firstText(body, "paymentDetails.0.paymentMode", "paymentDetails.paymentMode", "data.paymentDetails.0.paymentMode", "paymentMode");
            String transactionId = firstText(body, "paymentDetails.0.transactionId", "paymentDetails.transactionId", "data.paymentDetails.0.transactionId", "transactionId");
            boolean success = isSuccessState(state) || isSuccessState(paymentState);
            return PaymentStatusResponse.builder()
                    .provider("PHONEPE")
                    .merchantOrderId(merchantOrderId)
                    .transactionId(transactionId)
                    .state(state)
                    .paymentState(paymentState)
                    .paymentMode(paymentMode)
                    .success(success)
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("Unable to verify PhonePe payment");
        }
    }

    @Override
    public boolean verifyPayment(PlaceOrderRequest request, BigDecimal amount) {
        String merchantOrderId = firstNonBlank(request.getPhonepeMerchantOrderId(), request.getRazorpayOrderId());
        return getPaymentStatus(merchantOrderId).isSuccess();
    }

    private boolean isConfigured() {
        return !isBlank(appProperties.getPhonepe().getClientId())
                && !isBlank(appProperties.getPhonepe().getClientSecret())
                && !isBlank(appProperties.getPhonepe().getRedirectUrl());
    }

    private String fetchAccessToken() throws IOException, InterruptedException {
        Instant expiresAt = cachedAccessTokenExpiresAt;
        if (!isBlank(cachedAccessToken) && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedAccessToken;
        }
        synchronized (this) {
            expiresAt = cachedAccessTokenExpiresAt;
            if (!isBlank(cachedAccessToken) && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
                return cachedAccessToken;
            }

            String payload = "client_id=" + encode(appProperties.getPhonepe().getClientId())
                    + "&client_version=" + encode(String.valueOf(appProperties.getPhonepe().getClientVersion()))
                    + "&client_secret=" + encode(appProperties.getPhonepe().getClientSecret())
                    + "&grant_type=client_credentials";

            HttpRequest request = HttpRequest.newBuilder(URI.create(getIdentityUrl()))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .build();
            JsonNode body = readResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            String accessToken = firstText(body, "access_token", "data.accessToken", "token");
            long expiresIn = parseLong(firstText(body, "expires_in", "data.expiresIn"), 900L);
            if (isBlank(accessToken)) {
                throw new BusinessException(firstText(body, "message", "error", "Unable to fetch PhonePe access token"));
            }
            cachedAccessToken = accessToken;
            cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn, 120L));
            return accessToken;
        }
    }

    private JsonNode readResponse(HttpResponse<String> response) throws IOException {
        JsonNode body = objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new BusinessException(firstText(body, "message", "error.message", "error", "PhonePe request failed"));
        }
        return body;
    }

    private String getIdentityUrl() {
        if (isSandbox()) {
            return "https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token";
        }
        return "https://api.phonepe.com/apis/identity-manager/v1/oauth/token";
    }

    private String getApiBaseUrl() {
        if (isSandbox()) {
            return "https://api-preprod.phonepe.com/apis/pg-sandbox";
        }
        return "https://api.phonepe.com/apis/pg";
    }

    private boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(appProperties.getPhonepe().getEnv())
                || "preprod".equalsIgnoreCase(appProperties.getPhonepe().getEnv());
    }

    private String appendMerchantOrderId(String baseUrl, String merchantOrderId) {
        String separator = baseUrl != null && baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "phonepeMerchantOrderId=" + encode(merchantOrderId);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isSuccessState(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.equals("SUCCESS")
                || normalized.equals("COMPLETED")
                || normalized.equals("PAYMENT_SUCCESS")
                || normalized.equals("CHARGED");
    }

    private String firstText(JsonNode body, String... paths) {
        for (String path : paths) {
            JsonNode node = body;
            for (String item : path.split("\\.")) {
                if (node == null || node.isMissingNode() || node.isNull()) {
                    node = null;
                    break;
                }
                if (item.matches("\\d+")) {
                    int index = Integer.parseInt(item);
                    node = node.isArray() && node.size() > index ? node.get(index) : null;
                } else {
                    node = node.get(item);
                }
            }
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String text = node.asText();
                if (!isBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private long parseLong(String value, long fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
