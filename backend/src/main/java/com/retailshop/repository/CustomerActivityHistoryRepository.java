package com.retailshop.repository;

import com.retailshop.entity.CustomerActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerActivityHistoryRepository extends JpaRepository<CustomerActivityHistory, UUID> {
    List<CustomerActivityHistory> findTop30ByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
