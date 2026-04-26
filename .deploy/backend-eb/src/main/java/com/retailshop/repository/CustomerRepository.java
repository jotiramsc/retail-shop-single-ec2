package com.retailshop.repository;

import com.retailshop.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByMobile(String mobile);

    @Query("""
            select c
            from Customer c
            where lower(c.name) like lower(concat('%', :query, '%'))
               or c.mobile like concat('%', :query, '%')
            order by c.name asc
            """)
    List<Customer> searchByNameOrMobile(String query);
}
