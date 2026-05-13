package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.dto.PaymentOrderResponse;
import com.retailshop.dto.PaymentStatusResponse;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.entity.PaymentTransaction;
import com.retailshop.exception.BusinessException;
import com.retailshop.service.PaymentService;
import com.retailshop.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RazorpayPaymentService implements PaymentService {

    private static final String PROVIDER = "RAZORPAY";
    private static final String API_BASE_URL = "https://api.razorpay.com/v1";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final PaymentTransactionService paymentTransactionService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public PaymentOrderResponse createPaymentOrder(BigDecimal amount, String receipt, String redirectUrl) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        long amountSubunits = safeAmount.movePointRight(2).longValue();
        String localOrderId = "RZP-" + System.currentTimeMillis();

        if (!isConfigured()) {
            recordPaymentTransaction("CREATE_ORDER", "LOCAL_TEST", localOrderId, null, receipt, "INR",
                    safeAmount, amountSubunits, "local", "LOCAL_TEST",
                    Map.of("amount", amountSubunits, "currency", "INR", "receipt", trimReceipt(receipt)),
                    Map.of("configured", false, "reason", "Razorpay key id/key secret is not configured"),
                    null);
            return PaymentOrderResponse.builder()
                    .provider(PROVIDER)
                    .configured(!isBlank(appProperties.getRazorpay().getKeyId()))
                    .keyId(trimToEmpty(appProperties.getRazorpay().getKeyId()))
                    .orderId(localOrderId)
                    .receipt(receipt)
                    .currency("INR")
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .build();
        }

        Map<String, Object> createOrderRequest = new LinkedHashMap<>();
        createOrderRequest.put("amount", amountSubunits);
        createOrderRequest.put("currency", "INR");
        createOrderRequest.put("receipt", trimReceipt(receipt));
        createOrderRequest.put("notes", Map.of("source", "website_checkout"));

        String payload = paymentTransactionService.toJson(createOrderRequest);
        String createOrderResponsePayload = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE_URL + "/orders"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", basicAuthHeader())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            createOrderResponsePayload = response.body();
            JsonNode body = readResponse(response);
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
            recordPaymentTransaction("CREATE_ORDER", "SUCCESS", orderId, null, firstNonBlank(firstText(body, "receipt"), receipt),
                    firstNonBlank(firstText(body, "currency"), "INR"), safeAmount, amountSubunits, firstText(body, "status"),
                    firstText(body, "status"), createOrderRequest, body, null);
            return PaymentOrderResponse.builder()
                    .provider(PROVIDER)
                    .configured(true)
                    .keyId(appProperties.getRazorpay().getKeyId().trim())
                    .orderId(orderId)
                    .receipt(firstNonBlank(firstText(body, "receipt"), receipt))
                    .currency(firstNonBlank(firstText(body, "currency"), "INR"))
                    .amount(safeAmount)
                    .amountSubunits(amountSubunits)
                    .build();
        } catch (BusinessException exception) {
            recordPaymentTransaction("CREATE_ORDER", "FAILED", null, null, receipt, "INR",
                    safeAmount, amountSubunits, null, "FAILED", createOrderRequest, createOrderResponsePayload, exception.getMessage());
            throw exception;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordPaymentTransaction("CREATE_ORDER", "FAILED", null, null, receipt, "INR",
                    safeAmount, amountSubunits, null, "FAILED", createOrderRequest, createOrderResponsePayload, exception.getMessage());
            throw new BusinessException("Unable to create Razorpay payment");
        }
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(String orderId) {
        if (isBlank(orderId)) {
            throw new BusinessException("Razorpay order id is required");
        }
        if (!isConfigured()) {
            recordPaymentTransaction("STATUS_CHECK", "LOCAL_TEST", orderId, null, null, "INR",
                    null, null, "LOCAL_TEST", "SUCCESS",
                    Map.of("orderId", orderId), Map.of("configured", false, "state", "LOCAL_TEST"), null);
            return PaymentStatusResponse.builder()
                    .provider(PROVIDER)
                    .merchantOrderId(orderId)
                    .state("LOCAL_TEST")
                    .paymentState("SUCCESS")
                    .paymentMode("TEST")
                    .transactionId("local-" + orderId)
                    .success(true)
                    .build();
        }

        String statusResponsePayload = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE_URL + "/orders/" + encode(orderId)))
                    .GET()
                    .header("Accept", "application/json")
                    .header("Authorization", basicAuthHeader())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            statusResponsePayload = response.body();
            JsonNode body = readResponse(response);
            String status = firstText(body, "status");
            long amountPaid = parseLong(firstText(body, "amount_paid"), 0L);
            boolean success = "paid".equalsIgnoreCase(status) || amountPaid > 0;
            recordPaymentTransaction("STATUS_CHECK", success ? "SUCCESS" : "PENDING", orderId, null,
                    firstText(body, "receipt"), firstNonBlank(firstText(body, "currency"), "INR"),
                    BigDecimal.valueOf(parseLong(firstText(body, "amount"), 0L)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP),
                    parseLong(firstText(body, "amount"), 0L), status, success ? "SUCCESS" : status,
                    Map.of("orderId", orderId), body, null);
            return PaymentStatusResponse.builder()
                    .provider(PROVIDER)
                    .merchantOrderId(orderId)
                    .state(status)
                    .paymentState(success ? "SUCCESS" : status)
                    .paymentMode(PROVIDER)
                    .transactionId(orderId)
                    .success(success)
                    .build();
        } catch (BusinessException exception) {
            recordPaymentTransaction("STATUS_CHECK", "FAILED", orderId, null, null, null,
                    null, null, null, "FAILED", Map.of("orderId", orderId), statusResponsePayload, exception.getMessage());
            throw exception;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordPaymentTransaction("STATUS_CHECK", "FAILED", orderId, null, null, null,
                    null, null, null, "FAILED", Map.of("orderId", orderId), statusResponsePayload, exception.getMessage());
            throw new BusinessException("Unable to verify Razorpay payment");
        }
    }

    @Override
    public boolean verifyPayment(PlaceOrderRequest request, BigDecimal amount) {
        if (!isConfigured()) {
            boolean verified = !isBlank(request.getRazorpayOrderId());
            recordPaymentTransaction("VERIFY_PAYMENT", verified ? "LOCAL_TEST" : "FAILED",
                    request.getRazorpayOrderId(), request.getRazorpayPaymentId(), null, "INR", amount,
                    toSubunits(amount), null, verified ? "SUCCESS" : "MISSING_ORDER_ID",
                    verificationDiagnostics(request, false), Map.of("configured", false, "verified", verified), null);
            return verified;
        }
        if (isBlank(request.getRazorpayOrderId()) || isBlank(request.getRazorpayPaymentId()) || isBlank(request.getRazorpaySignature())) {
            recordPaymentTransaction("VERIFY_PAYMENT", "FAILED", request.getRazorpayOrderId(), request.getRazorpayPaymentId(),
                    null, "INR", amount, toSubunits(amount), null, "MISSING_FIELDS",
                    verificationDiagnostics(request, true), null, "Razorpay order id, payment id, and signature are required");
            return false;
        }
        String payload = request.getRazorpayOrderId().trim() + "|" + request.getRazorpayPaymentId().trim();
        boolean verified = constantTimeEquals(hmacSha256(payload, appProperties.getRazorpay().getKeySecret()), request.getRazorpaySignature().trim());
        recordPaymentTransaction("VERIFY_PAYMENT", verified ? "SUCCESS" : "FAILED", request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(), null, "INR", amount, toSubunits(amount), null,
                verified ? "SUCCESS" : "SIGNATURE_MISMATCH",
                verificationDiagnostics(request, true), Map.of("verified", verified),
                verified ? null : "Razorpay signature did not match the order/payment payload");
        return verified;
    }

    private void recordPaymentTransaction(String operation,
                                          String status,
                                          String gatewayOrderId,
                                          String gatewayPaymentId,
                                          String receipt,
                                          String currency,
                                          BigDecimal amount,
                                          Long amountSubunits,
                                          String gatewayStatus,
                                          String paymentState,
                                          Object requestPayload,
                                          Object responsePayload,
                                          String errorMessage) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setProvider(PROVIDER);
        transaction.setOperation(operation);
        transaction.setStatus(status);
        transaction.setGatewayOrderId(trimToNull(gatewayOrderId));
        transaction.setGatewayPaymentId(trimToNull(gatewayPaymentId));
        transaction.setReceipt(trimToNull(receipt));
        transaction.setCurrency(trimToNull(currency));
        transaction.setAmount(amount);
        transaction.setAmountSubunits(amountSubunits);
        transaction.setGatewayStatus(trimToNull(gatewayStatus));
        transaction.setPaymentState(trimToNull(paymentState));
        transaction.setRequestPayload(toPayload(requestPayload));
        transaction.setResponsePayload(toPayload(responsePayload));
        transaction.setErrorMessage(trimToNull(errorMessage));
        paymentTransactionService.record(transaction);
    }

    private Map<String, Object> verificationDiagnostics(PlaceOrderRequest request, boolean configured) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configured", configured);
        payload.put("orderIdPresent", !isBlank(request.getRazorpayOrderId()));
        payload.put("paymentIdPresent", !isBlank(request.getRazorpayPaymentId()));
        payload.put("signaturePresent", !isBlank(request.getRazorpaySignature()));
        payload.put("paymentProvider", trimToEmpty(request.getPaymentProvider()));
        return payload;
    }

    private String toPayload(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.toString();
        }
        return paymentTransactionService.toJson(value);
    }

    private Long toSubunits(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
    }

    private boolean isConfigured() {
        return !isBlank(appProperties.getRazorpay().getKeyId())
                && !isBlank(appProperties.getRazorpay().getKeySecret());
    }

    private JsonNode readResponse(HttpResponse<String> response) throws IOException {
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

    private String basicAuthHeader() {
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

    private String firstText(JsonNode body, String... paths) {
        for (String path : paths) {
            JsonNode node = body;
            for (String part : path.split("\\.")) {
                node = node.path(part);
            }
            String value = node.asText(null);
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String firstTextOrDefault(JsonNode body, String fallback, String... paths) {
        String value = firstText(body, paths);
        return isBlank(value) ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(trimToEmpty(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
