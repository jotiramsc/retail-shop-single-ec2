package com.retailshop.repository;

import com.retailshop.entity.CustomerReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerReviewRepository extends JpaRepository<CustomerReview, UUID> {
    Page<CustomerReview> findByApprovedTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<CustomerReview> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
