package com.retailshop.controller;

import com.retailshop.dto.SiteInteractionReportResponse;
import com.retailshop.dto.SiteInteractionSummaryResponse;
import com.retailshop.dto.SiteVisitRequest;
import com.retailshop.service.SiteInteractionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site-interactions")
@RequiredArgsConstructor
public class SiteInteractionController {

    private final SiteInteractionService siteInteractionService;

    @PostMapping("/visit")
    public SiteInteractionSummaryResponse recordVisit(@RequestBody(required = false) SiteVisitRequest request,
                                                      HttpServletRequest httpServletRequest) {
        return siteInteractionService.recordVisit(
                request,
                httpServletRequest.getHeader("User-Agent"),
                httpServletRequest.getHeader("Accept-Language"),
                firstNonBlank(
                        httpServletRequest.getHeader("X-Forwarded-For"),
                        httpServletRequest.getHeader("X-Real-IP"),
                        httpServletRequest.getRemoteAddr()
                )
        );
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('PERM_REPORTS')")
    public SiteInteractionReportResponse getReport(@RequestParam(defaultValue = "30") int days) {
        return siteInteractionService.getReport(days);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
