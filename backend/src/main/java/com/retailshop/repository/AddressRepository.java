package com.retailshop.repository;

import com.retailshop.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
