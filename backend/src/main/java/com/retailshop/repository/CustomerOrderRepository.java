package com.retailshop.repository;

import com.retailshop.entity.CustomerOrder;
import com.retailshop.enums.OrderSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findByCustomerIdAndSourceOrderByCreatedAtDesc(UUID customerId, OrderSource source);

    @EntityGraph(attributePaths = {"items"})
    Optional<CustomerOrder> findByInvoiceId(UUID invoiceId);

    @EntityGraph(attributePaths = {"items"})
    Optional<CustomerOrder> findByPaymentOrderId(String paymentOrderId);

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            String customerName
    );
}
