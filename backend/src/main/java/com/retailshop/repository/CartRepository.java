package com.retailshop.repository;

import com.retailshop.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByCustomerId(UUID customerId);
}
