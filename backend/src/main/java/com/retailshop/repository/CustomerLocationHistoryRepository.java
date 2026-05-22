package com.retailshop.repository;

import com.retailshop.entity.CustomerLocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerLocationHistoryRepository extends JpaRepository<CustomerLocationHistory, UUID> {
    List<CustomerLocationHistory> findTop10ByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    Optional<CustomerLocationHistory> findFirstByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
