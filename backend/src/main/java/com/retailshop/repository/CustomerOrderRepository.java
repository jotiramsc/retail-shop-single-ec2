package com.retailshop.repository;

import com.retailshop.entity.CustomerOrder;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    @Query(value = "select nextval('customer_order_number_seq')", nativeQuery = true)
    Long nextOrderNumberValue();

    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findByCustomerIdAndSourceOrderByCreatedAtDesc(UUID customerId, OrderSource source);

    @EntityGraph(attributePaths = {"items"})
    Optional<CustomerOrder> findByInvoiceId(UUID invoiceId);

    @EntityGraph(attributePaths = {"items"})
    Optional<CustomerOrder> findByPaymentOrderId(String paymentOrderId);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<CustomerOrder> findByOrderNumberIgnoreCase(String orderNumber);

    @EntityGraph(attributePaths = {"customer", "items"})
    List<CustomerOrder> findTop3ByCustomer_MobileContainingOrderByCreatedAtDesc(String mobile);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<CustomerOrder> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<CustomerOrder> findBySourceOrderByCreatedAtDesc(OrderSource source);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<CustomerOrder> findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            String customerName
    );

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatusIn(Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.finalAmount), 0) from CustomerOrder o where o.createdAt between :start and :end")
    BigDecimal sumFinalAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(o.finalAmount), 0) from CustomerOrder o")
    BigDecimal sumFinalAmount();

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findTop10ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
