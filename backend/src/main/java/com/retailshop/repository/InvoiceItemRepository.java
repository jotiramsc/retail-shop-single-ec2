package com.retailshop.repository;

import com.retailshop.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    boolean existsByProductId(UUID productId);

    @Query("""
            select ii.product.id, coalesce(sum(ii.quantity), 0)
            from InvoiceItem ii
            group by ii.product.id
            """)
    List<Object[]> aggregateQuantitySoldByProduct();

    @Query("""
            select ii.product.id, coalesce(sum(ii.quantity), 0)
            from InvoiceItem ii
            group by ii.product.id
            order by coalesce(sum(ii.quantity), 0) desc
            """)
    List<Object[]> findTrendingProductSales();

    void deleteByInvoiceId(UUID invoiceId);
}
