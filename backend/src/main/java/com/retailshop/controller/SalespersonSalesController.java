package com.retailshop.controller;

import com.retailshop.dto.SalespersonSalesResponse;
import com.retailshop.entity.StaffUser;
import com.retailshop.service.SalespersonSalesService;
import com.retailshop.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/salesperson-sales")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_SALESPERSON_SALES')")
public class SalespersonSalesController {

    private final SalespersonSalesService salespersonSalesService;
    private final StaffUserService staffUserService;

    @GetMapping
    public SalespersonSalesResponse getSalespersonSales(Authentication authentication,
                                                        @RequestParam(required = false) String salespersonId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                        @RequestParam(defaultValue = "DAILY") String viewType) {
        StaffUser viewer = staffUserService.getByUsername(authentication.getName());
        return salespersonSalesService.getSalespersonSales(viewer, salespersonId, fromDate, toDate, viewType);
    }
}
