package com.retailshop.repository;

import com.retailshop.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Invoice> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    List<Invoice> findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            String customerName
    );

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Page<Invoice> findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            String customerName,
            Pageable pageable
    );

    List<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("select coalesce(sum(i.finalAmount), 0) from Invoice i where i.createdAt between :start and :end")
    java.math.BigDecimal sumFinalAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.discount), 0) from Invoice i where i.createdAt between :start and :end")
    java.math.BigDecimal sumDiscountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
