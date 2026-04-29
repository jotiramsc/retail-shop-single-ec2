package com.retailshop.service;

import com.retailshop.dto.SalespersonSalesResponse;
import com.retailshop.entity.StaffUser;

import java.time.LocalDate;

public interface SalespersonSalesService {
    SalespersonSalesResponse getSalespersonSales(StaffUser viewer,
                                                 String salespersonId,
                                                 LocalDate fromDate,
                                                 LocalDate toDate,
                                                 String viewType);
}
