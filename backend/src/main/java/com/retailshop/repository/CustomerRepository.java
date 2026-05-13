package com.retailshop.repository;

import com.retailshop.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByMobile(String mobile);
    Optional<Customer> findByEmailIgnoreCase(String email);
    Optional<Customer> findByGoogleSubject(String googleSubject);
    Page<Customer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = """
            select *
            from customers c
            where right(regexp_replace(coalesce(c.mobile, ''), '[^0-9]', '', 'g'), 10) = :mobile
            limit 1
            """, nativeQuery = true)
    Optional<Customer> findByNormalizedMobile(String mobile);

    @Query("""
            select c
            from Customer c
            where lower(coalesce(c.name, '')) like lower(concat('%', :query, '%'))
               or coalesce(c.mobile, '') like concat('%', :query, '%')
               or lower(coalesce(c.email, '')) like lower(concat('%', :query, '%'))
            order by coalesce(c.name, c.email, c.mobile) asc
            """)
    List<Customer> searchByNameOrMobile(String query);
}
