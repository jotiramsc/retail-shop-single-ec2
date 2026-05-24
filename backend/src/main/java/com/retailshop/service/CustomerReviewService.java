package com.retailshop.service;

import com.retailshop.dto.CustomerReviewModerationRequest;
import com.retailshop.dto.CustomerReviewRequest;
import com.retailshop.dto.CustomerReviewResponse;
import com.retailshop.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerReviewService {
    PaginatedResponse<CustomerReviewResponse> getPublicReviews(Pageable pageable);
    PaginatedResponse<CustomerReviewResponse> getAdminReviews(Pageable pageable);
    CustomerReviewResponse submit(CustomerReviewRequest request);
    CustomerReviewResponse moderate(UUID id, CustomerReviewModerationRequest request);
    void delete(UUID id);
}
