package com.retailshop.repository;

import com.retailshop.entity.StaffUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {
    Optional<StaffUser> findByUsername(String username);
    boolean existsByUsername(String username);
    Page<StaffUser> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
