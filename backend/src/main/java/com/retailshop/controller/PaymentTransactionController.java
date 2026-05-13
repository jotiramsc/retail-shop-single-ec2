package com.retailshop.controller;

import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.PaymentTransactionResponse;
import com.retailshop.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_REPORTS')")
public class PaymentTransactionController {

    private final PaymentTransactionService paymentTransactionService;

    @GetMapping("/transactions")
    public PaginatedResponse<PaymentTransactionResponse> transactions(@RequestParam(required = false) LocalDate fromDate,
                                                                      @RequestParam(required = false) LocalDate toDate,
                                                                      @RequestParam(required = false) String provider,
                                                                      @RequestParam(required = false) String operation,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String search,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return paymentTransactionService.search(fromDate, toDate, provider, operation, status, search, pageable);
    }
}
