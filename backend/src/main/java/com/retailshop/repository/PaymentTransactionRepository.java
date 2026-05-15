package com.retailshop.repository;

import com.retailshop.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByGatewayOrderIdOrderByCreatedAtDesc(String gatewayOrderId);

    Optional<PaymentTransaction> findFirstByGatewayOrderIdOrderByCreatedAtDesc(String gatewayOrderId);

    @Query("""
            select p from PaymentTransaction p
            where p.createdAt between :start and :end
              and (:provider is null or p.provider = :provider)
              and (:operation is null or p.operation = :operation)
              and (:status is null or p.status = :status)
            order by p.createdAt desc
            """)
    Page<PaymentTransaction> search(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("provider") String provider,
                                    @Param("operation") String operation,
                                    @Param("status") String status,
                                    Pageable pageable);

    @Query("""
            select p from PaymentTransaction p
            where p.createdAt between :start and :end
              and (:provider is null or p.provider = :provider)
              and (:operation is null or p.operation = :operation)
              and (:status is null or p.status = :status)
              and (lower(coalesce(p.gatewayOrderId, '')) like :searchPattern
                   or lower(coalesce(p.gatewayPaymentId, '')) like :searchPattern
                   or lower(coalesce(p.orderNumber, '')) like :searchPattern
                   or lower(coalesce(p.receipt, '')) like :searchPattern
                   or lower(coalesce(p.errorMessage, '')) like :searchPattern)
            order by p.createdAt desc
            """)
    Page<PaymentTransaction> searchWithText(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("provider") String provider,
                                            @Param("operation") String operation,
                                            @Param("status") String status,
                                            @Param("searchPattern") String searchPattern,
                                            Pageable pageable);
}
