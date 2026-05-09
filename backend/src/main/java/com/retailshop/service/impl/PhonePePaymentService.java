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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class PhonePePaymentService implements PaymentService {

    private static final String PHONEPE_PROVIDER = "PHONEPE";
    private static final String RAZORPAY_PROVIDER = "RAZORPAY";
    private static final String RAZORPAY_API_BASE_URL = "https://api.razorpay.com/v1";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt;

    @Override
    public PaymentOrderResponse createPaymentOrder(BigDecimal amount, String receipt, String requestedRedirectUrl) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long amountSubunits = safeAmount.movePointRight(2).longValue();
        if (isRazorpayProvider()) {
            return createRazorpayPaymentOrder(safeAmount, amountSubunits, receipt);
        }

        String merchantOrderId = "PP-" + System.currentTimeMillis();

        if (!isPhonePeConfigured()) {
            return PaymentOrderResponse.builder()
                    .provider(PHONEPE_PROVIDER)
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
            String redirectUrl = resolveRedirectUrl(requestedRedirectUrl, merchantOrderId);
            if (redirectUrl == null) {
                throw new BusinessException("PhonePe checkout needs a valid absolute checkout URL. Configure PHONEPE_REDIRECT_URL or open the site from its public address before paying.");
            }
            String callbackUrl = resolveCallbackUrl();
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
                throw new BusinessException(firstTextOrDefault(
                        body,
                        "Unable to create PhonePe payment",
                        "message",
                        "error.message",
                        "error.description",
                        "details.0.message",
                        "error"
                ));
            }
            return PaymentOrderResponse.builder()
                    .provider(PHONEPE_PROVIDER)
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
        if (isRazorpayProvider() || merchantOrderId.startsWith("order_") || merchantOrderId.startsWith("RZP-")) {
            return getRazorpayPaymentStatus(merchantOrderId);
        }
        if (!isPhonePeConfigured()) {
            return PaymentStatusResponse.builder()
                    .provider(PHONEPE_PROVIDER)
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
                    .provider(PHONEPE_PROVIDER)
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
        if (!isBlank(request.getRazorpayOrderId()) || RAZORPAY_PROVIDER.equalsIgnoreCase(request.getPaymentProvider())) {
            return verifyRazorpayPayment(request);
        }
        String merchantOrderId = request.getPhonepeMerchantOrderId();
        return getPaymentStatus(merchantOrderId).isSuccess();
    }

    private PaymentOrderResponse createRazorpayPaymentOrder(BigDecimal safeAmount, long amountSubunits, String receipt) {
        String localOrderId = "RZP-" + System.currentTimeMillis();
        if (!isRazorpayConfigured()) {
            return PaymentOrderResponse.builder()
                    .provider(RAZORPAY_PROVIDER)
                    .configured(false)
                    .keyId(trimToEmpty(appProperties.getRazorpay().getKeyId()))
                    .orderId(localOrderId)
                    .receipt(receipt)
                    .currency("INR")
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .build();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "amount", amountSubunits,
                    "currency", "INR",
                    "receipt", trimReceipt(receipt),
                    "notes", Map.of("source", "website_checkout")
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(RAZORPAY_API_BASE_URL + "/orders"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", razorpayBasicAuthHeader())
                    .build();
            JsonNode body = readRazorpayResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            String orderId = firstText(body, "id");
            if (isBlank(orderId)) {
                throw new BusinessException(firstTextOrDefault(
                        body,
                        "Unable to create Razorpay payment",
                        "error.description",
                        "error.reason",
                        "message"
                ));
            }
            return PaymentOrderResponse.builder()
                    .provider(RAZORPAY_PROVIDER)
                    .configured(true)
                    .keyId(appProperties.getRazorpay().getKeyId().trim())
                    .orderId(orderId)
                    .receipt(firstNonBlank(firstText(body, "receipt"), receipt))
                    .currency(firstNonBlank(firstText(body, "currency"), "INR"))
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("Unable to create Razorpay payment");
        }
    }

    private PaymentStatusResponse getRazorpayPaymentStatus(String orderId) {
        if (!isRazorpayConfigured()) {
            return PaymentStatusResponse.builder()
                    .provider(RAZORPAY_PROVIDER)
                    .merchantOrderId(orderId)
                    .state("LOCAL_TEST")
                    .paymentState("SUCCESS")
                    .paymentMode("TEST")
                    .transactionId("local-" + orderId)
                    .success(true)
                    .build();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RAZORPAY_API_BASE_URL + "/orders/" + encode(orderId)))
                    .GET()
                    .header("Accept", "application/json")
                    .header("Authorization", razorpayBasicAuthHeader())
                    .build();
            JsonNode body = readRazorpayResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
            String status = firstText(body, "status");
            long amountPaid = parseLong(firstText(body, "amount_paid"), 0L);
            boolean success = "paid".equalsIgnoreCase(status) || amountPaid > 0;
            return PaymentStatusResponse.builder()
                    .provider(RAZORPAY_PROVIDER)
                    .merchantOrderId(orderId)
                    .state(status)
                    .paymentState(success ? "SUCCESS" : status)
                    .paymentMode(RAZORPAY_PROVIDER)
                    .transactionId(orderId)
                    .success(success)
                    .build();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("Unable to verify Razorpay payment");
        }
    }

    private boolean verifyRazorpayPayment(PlaceOrderRequest request) {
        if (!isRazorpayConfigured()) {
            return !isBlank(request.getRazorpayOrderId());
        }
        if (isBlank(request.getRazorpayOrderId()) || isBlank(request.getRazorpayPaymentId()) || isBlank(request.getRazorpaySignature())) {
            return false;
        }
        String payload = request.getRazorpayOrderId().trim() + "|" + request.getRazorpayPaymentId().trim();
        return constantTimeEquals(hmacSha256(payload, appProperties.getRazorpay().getKeySecret()), request.getRazorpaySignature().trim());
    }

    private boolean isRazorpayProvider() {
        return RAZORPAY_PROVIDER.equalsIgnoreCase(trimToEmpty(appProperties.getPayment().getProvider()));
    }

    private boolean isRazorpayConfigured() {
        return !isBlank(appProperties.getRazorpay().getKeyId())
                && !isBlank(appProperties.getRazorpay().getKeySecret());
    }

    private boolean isPhonePeConfigured() {
        return !isBlank(appProperties.getPhonepe().getClientId())
                && !isBlank(appProperties.getPhonepe().getClientSecret());
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new BusinessException("PhonePe authentication failed. Check the client id, client secret, environment, and client version configured on the server.");
            }
            JsonNode body = readResponse(response);
            String accessToken = firstText(body, "access_token", "data.accessToken", "token");
            long expiresIn = parseLong(firstText(body, "expires_in", "data.expiresIn"), 900L);
            if (isBlank(accessToken)) {
                throw new BusinessException(firstTextOrDefault(
                        body,
                        "Unable to fetch PhonePe access token",
                        "message",
                        "error.message",
                        "error.description",
                        "details.0.message",
                        "error"
                ));
            }
            cachedAccessToken = accessToken;
            cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn, 120L));
            return accessToken;
        }
    }

    private JsonNode readResponse(HttpResponse<String> response) throws IOException {
        JsonNode body = objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new BusinessException(firstTextOrDefault(
                    body,
                    "PhonePe request failed",
                    "message",
                    "error.message",
                    "error.description",
                    "details.0.message",
                    "error"
            ));
        }
        return body;
    }

    private JsonNode readRazorpayResponse(HttpResponse<String> response) throws IOException {
        JsonNode body = objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new BusinessException(firstTextOrDefault(
                    body,
                    "Razorpay request failed",
                    "error.description",
                    "error.reason",
                    "error.source",
                    "message"
            ));
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

    private String resolveRedirectUrl(String requestedRedirectUrl, String merchantOrderId) {
        String requested = sanitizeUrl(requestedRedirectUrl);
        if (isAllowedRedirectUrl(requested)) {
            return appendMerchantOrderId(requested, merchantOrderId);
        }
        String configured = sanitizeUrl(appProperties.getPhonepe().getRedirectUrl());
        if (isAllowedRedirectUrl(configured)) {
            return appendMerchantOrderId(configured, merchantOrderId);
        }
        return null;
    }

    private String resolveCallbackUrl() {
        String callbackUrl = sanitizeUrl(appProperties.getPhonepe().getWebhookUrl());
        return isAllowedRedirectUrl(callbackUrl) ? callbackUrl : null;
    }

    private String sanitizeUrl(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isAllowedRedirectUrl(String value) {
        return isAbsoluteHttpUrl(value);
    }

    private boolean isAbsoluteHttpUrl(String value) {
        URI uri = parseUri(value);
        return uri != null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && !isBlank(uri.getHost());
    }

    private URI parseUri(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String razorpayBasicAuthHeader() {
        String credentials = appProperties.getRazorpay().getKeyId().trim() + ":" + appProperties.getRazorpay().getKeySecret().trim();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BusinessException("Unable to verify Razorpay payment");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                trimToEmpty(expected).getBytes(StandardCharsets.UTF_8),
                trimToEmpty(actual).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String trimReceipt(String receipt) {
        String trimmed = firstNonBlank(receipt, "checkout");
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
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

    private String firstTextOrDefault(JsonNode body, String fallback, String... paths) {
        String text = firstText(body, paths);
        return !isBlank(text) ? text : fallback;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
