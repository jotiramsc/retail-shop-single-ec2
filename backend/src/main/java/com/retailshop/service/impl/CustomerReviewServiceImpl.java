package com.retailshop.service.impl;

import com.retailshop.dto.CustomerReviewModerationRequest;
import com.retailshop.dto.CustomerReviewRequest;
import com.retailshop.dto.CustomerReviewResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.CustomerReview;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerReviewRepository;
import com.retailshop.service.CustomerReviewService;
import com.retailshop.util.MobileNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerReviewServiceImpl implements CustomerReviewService {

    private final CustomerReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<CustomerReviewResponse> getPublicReviews(Pageable pageable) {
        return PaginatedResponse.from(reviewRepository.findByApprovedTrueOrderByCreatedAtDesc(pageable).map(this::map));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<CustomerReviewResponse> getAdminReviews(Pageable pageable) {
        return PaginatedResponse.from(reviewRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::map));
    }

    @Override
    @Transactional
    public CustomerReviewResponse submit(CustomerReviewRequest request) {
        String normalizedMobile = MobileNumberUtils.normalizeIndianMobile(request.getMobile());
        if (!normalizedMobile.matches("^[6-9]\\d{9}$")) {
            throw new BusinessException("Mobile must be a valid 10-digit number");
        }
        CustomerReview review = new CustomerReview();
        review.setCustomerName(firstNonBlank(request.getCustomerName(), "Store visitor"));
        review.setMobile(normalizedMobile);
        review.setCity(optionalText(request.getCity()));
        review.setProduct(optionalText(request.getProduct()));
        review.setRating(request.getRating());
        review.setComment(firstNonBlank(request.getComment(), "Shared storefront feedback."));
        review.setApproved(false);
        return map(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public CustomerReviewResponse moderate(UUID id, CustomerReviewModerationRequest request) {
        CustomerReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (request != null && request.getApproved() != null) {
            review.setApproved(request.getApproved());
        }
        return map(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!reviewRepository.existsById(id)) {
            throw new ResourceNotFoundException("Review not found");
        }
        reviewRepository.deleteById(id);
    }

    private CustomerReviewResponse map(CustomerReview review) {
        return CustomerReviewResponse.builder()
                .id(review.getId())
                .customerName(review.getCustomerName())
                .mobile(review.getMobile())
                .city(review.getCity())
                .product(review.getProduct())
                .rating(review.getRating())
                .comment(review.getComment())
                .approved(Boolean.TRUE.equals(review.getApproved()))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String value, String fallback) {
        String text = optionalText(value);
        return text == null ? fallback : text;
    }
}
