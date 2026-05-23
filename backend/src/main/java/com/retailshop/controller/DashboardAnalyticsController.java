package com.retailshop.controller;

import com.retailshop.dto.DashboardAnalyticsResponse;
import com.retailshop.service.DashboardAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardAnalyticsController {

    private final DashboardAnalyticsService dashboardAnalyticsService;

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasAnyAuthority('PERM_REPORTS', 'PERM_CUSTOMERS_DASHBOARD', 'PERM_REPORTS_DASHBOARD')")
    public DashboardAnalyticsResponse getAnalytics() {
        return dashboardAnalyticsService.getDashboardAnalytics();
    }
}
