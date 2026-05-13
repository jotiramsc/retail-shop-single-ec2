package com.retailshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(nullable = false, length = 80)
    private String status;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(length = 100)
    private String receipt;

    @Column(length = 20)
    private String currency;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_subunits")
    private Long amountSubunits;

    @Column(name = "payment_state", length = 100)
    private String paymentState;

    @Column(name = "gateway_status", length = 100)
    private String gatewayStatus;

    @Column(name = "webhook_event")
    private String webhookEvent;

    @Column(name = "signature_status", length = 100)
    private String signatureStatus;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "request_payload", columnDefinition = "text")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
