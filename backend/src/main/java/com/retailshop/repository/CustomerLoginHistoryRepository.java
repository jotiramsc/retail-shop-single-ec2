package com.retailshop.repository;

import com.retailshop.entity.CustomerLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerLoginHistoryRepository extends JpaRepository<CustomerLoginHistory, UUID> {
    List<CustomerLoginHistory> findTop20ByCustomerIdOrderByLoginAtDesc(UUID customerId);
}
