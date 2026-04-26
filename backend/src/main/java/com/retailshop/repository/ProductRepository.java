package com.retailshop.repository;

import com.retailshop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);

    Page<Product> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select p from Product p where p.quantity <= p.lowStockThreshold")
    Page<Product> findLowStockProducts(Pageable pageable);
}
