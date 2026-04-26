package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ReportOrderFeedResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<ReportOrderRowResponse> orders;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
