package com.retailshop.controller;

import com.retailshop.dto.CustomerReviewModerationRequest;
import com.retailshop.dto.CustomerReviewRequest;
import com.retailshop.dto.CustomerReviewResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.service.CustomerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class CustomerReviewController {

    private final CustomerReviewService reviewService;

    @GetMapping("/public")
    public PaginatedResponse<CustomerReviewResponse> publicReviews(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "6") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 24));
        return reviewService.getPublicReviews(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerReviewResponse submitReview(@Valid @RequestBody CustomerReviewRequest request) {
        return reviewService.submit(request);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('PERM_CUSTOMERS')")
    public PaginatedResponse<CustomerReviewResponse> adminReviews(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return reviewService.getAdminReviews(pageable);
    }

    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('PERM_CUSTOMERS')")
    public CustomerReviewResponse moderateReview(@PathVariable UUID id,
                                                 @RequestBody CustomerReviewModerationRequest request) {
        return reviewService.moderate(id, request);
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable UUID id) {
        reviewService.delete(id);
    }
}
