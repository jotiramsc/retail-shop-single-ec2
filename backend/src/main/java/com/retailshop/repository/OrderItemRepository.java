package com.retailshop.repository;

import com.retailshop.entity.OrderItem;
import com.retailshop.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            select oi.product.id, coalesce(sum(oi.quantity), 0)
            from OrderItem oi
            where oi.product is not null
              and oi.order.paymentStatus = 'PAID'
              and oi.order.status <> :cancelledStatus
            group by oi.product.id
            order by coalesce(sum(oi.quantity), 0) desc,
                     max(oi.order.createdAt) desc,
                     max(oi.product.createdAt) desc
            """)
    List<Object[]> findTrendingProductSales(OrderStatus cancelledStatus);
}
