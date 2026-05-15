package com.retailshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.PaymentTransactionResponse;
import com.retailshop.entity.PaymentTransaction;
import com.retailshop.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private static final int MAX_PAYLOAD_LENGTH = 8_000;
    private static final int MAX_ERROR_LENGTH = 4_000;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentTransaction transaction) {
        try {
            transaction.setProvider(normalize(transaction.getProvider(), "RAZORPAY"));
            transaction.setOperation(normalize(transaction.getOperation(), "UNKNOWN"));
            transaction.setStatus(normalize(transaction.getStatus(), "RECORDED"));
            transaction.setRequestPayload(sanitizeAndTruncate(transaction.getRequestPayload()));
            transaction.setResponsePayload(sanitizeAndTruncate(transaction.getResponsePayload()));
            transaction.setErrorMessage(truncate(transaction.getErrorMessage(), MAX_ERROR_LENGTH));
            paymentTransactionRepository.save(transaction);
        } catch (RuntimeException exception) {
            log.warn("Unable to store payment transaction diagnostic", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachCustomerContext(String gatewayOrderId, UUID customerId) {
        if (!hasText(gatewayOrderId) || customerId == null) {
            return;
        }
        try {
            for (PaymentTransaction transaction : paymentTransactionRepository.findByGatewayOrderIdOrderByCreatedAtDesc(gatewayOrderId)) {
                if (transaction.getCustomerId() == null) {
                    transaction.setCustomerId(customerId);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to attach customer context to payment diagnostics for {}", gatewayOrderId, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkOrder(String gatewayOrderId, UUID orderId, String orderNumber, UUID customerId) {
        if (!hasText(gatewayOrderId) || orderId == null) {
            return;
        }
        try {
            for (PaymentTransaction transaction : paymentTransactionRepository.findByGatewayOrderIdOrderByCreatedAtDesc(gatewayOrderId)) {
                transaction.setOrderId(orderId);
                transaction.setOrderNumber(orderNumber);
                if (transaction.getCustomerId() == null) {
                    transaction.setCustomerId(customerId);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to link order to payment diagnostics for {}", gatewayOrderId, exception);
        }
    }

    public void recordWebhook(String payload, String signature, String signatureStatus) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setProvider("RAZORPAY");
        transaction.setOperation("WEBHOOK");
        transaction.setStatus("RECEIVED");
        transaction.setSignatureStatus(signatureStatus);
        transaction.setResponsePayload(payload);

        try {
            JsonNode root = objectMapper.readTree(payload == null ? "{}" : payload);
            transaction.setWebhookEvent(text(root.path("event")));

            JsonNode payment = root.path("payload").path("payment").path("entity");
            JsonNode order = root.path("payload").path("order").path("entity");
            JsonNode entity = payment.isMissingNode() || payment.isNull() ? order : payment;

            transaction.setGatewayPaymentId(text(payment.path("id")));
            transaction.setGatewayOrderId(firstText(payment.path("order_id"), order.path("id")));
            transaction.setGatewayStatus(text(entity.path("status")));
            transaction.setPaymentState(text(entity.path("status")));
            transaction.setCurrency(text(entity.path("currency")));
            transaction.setAmountSubunits(longValue(entity.path("amount")));
            if (transaction.getAmountSubunits() != null) {
                transaction.setAmount(BigDecimal.valueOf(transaction.getAmountSubunits()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
            }
            transaction.setFailureCode(firstText(entity.path("error_code"), entity.path("error_reason")));
            transaction.setErrorMessage(firstText(entity.path("error_description"), entity.path("description")));
        } catch (RuntimeException | java.io.IOException exception) {
            transaction.setStatus("PARSE_FAILED");
            transaction.setErrorMessage("Unable to parse Razorpay webhook payload: " + exception.getMessage());
        }

        record(transaction);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PaymentTransactionResponse> search(LocalDate fromDate,
                                                                LocalDate toDate,
                                                                String provider,
                                                                String operation,
                                                                String status,
                                                                String search,
                                                                Pageable pageable) {
        LocalDate rangeEnd = toDate != null ? toDate : LocalDate.now();
        LocalDate rangeStart = fromDate != null ? fromDate : rangeEnd.minusDays(7);
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate swap = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = swap;
        }
        LocalDateTime start = rangeStart.atStartOfDay();
        LocalDateTime end = rangeEnd.atTime(LocalTime.MAX);
        String normalizedSearch = normalizeFilter(search);
        var page = normalizedSearch == null
                ? paymentTransactionRepository.search(
                start,
                end,
                normalizeFilter(provider),
                normalizeFilter(operation),
                normalizeFilter(status),
                pageable
        )
                : paymentTransactionRepository.searchWithText(
                start,
                end,
                normalizeFilter(provider),
                normalizeFilter(operation),
                normalizeFilter(status),
                "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%",
                pageable
        );
        return PaginatedResponse.from(page.map(this::map));
    }

    private PaymentTransactionResponse map(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .provider(transaction.getProvider())
                .operation(transaction.getOperation())
                .status(transaction.getStatus())
                .customerId(transaction.getCustomerId())
                .orderId(transaction.getOrderId())
                .orderNumber(transaction.getOrderNumber())
                .gatewayOrderId(transaction.getGatewayOrderId())
                .gatewayPaymentId(transaction.getGatewayPaymentId())
                .receipt(transaction.getReceipt())
                .currency(transaction.getCurrency())
                .amount(transaction.getAmount())
                .amountSubunits(transaction.getAmountSubunits())
                .paymentState(transaction.getPaymentState())
                .gatewayStatus(transaction.getGatewayStatus())
                .webhookEvent(transaction.getWebhookEvent())
                .signatureStatus(transaction.getSignatureStatus())
                .failureCode(transaction.getFailureCode())
                .errorMessage(transaction.getErrorMessage())
                .requestPayload(transaction.getRequestPayload())
                .responsePayload(transaction.getResponsePayload())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException | java.io.IOException exception) {
            return "{}";
        }
    }

    public String sanitizeAndTruncate(String payload) {
        if (!hasText(payload)) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            return truncate(objectMapper.writeValueAsString(mask(root)), MAX_PAYLOAD_LENGTH);
        } catch (RuntimeException | java.io.IOException exception) {
            return truncate(payload, MAX_PAYLOAD_LENGTH);
        }
    }

    private JsonNode mask(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = copy.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveKey(entry.getKey())) {
                    copy.put(entry.getKey(), "redacted");
                } else {
                    copy.set(entry.getKey(), mask(entry.getValue()));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(item -> copy.add(mask(item)));
            return copy;
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("signature")
                || normalized.contains("card")
                || normalized.contains("vpa")
                || normalized.contains("email")
                || normalized.contains("contact")
                || normalized.contains("phone")
                || normalized.contains("mobile");
    }

    private String normalizeFilter(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalize(String value, String fallback) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 14) + "...[truncated]";
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private String firstText(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            String value = text(node);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Long longValue(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
