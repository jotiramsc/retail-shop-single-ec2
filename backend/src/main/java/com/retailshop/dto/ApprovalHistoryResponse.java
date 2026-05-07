package com.retailshop.dto;

import com.retailshop.enums.MarketingApprovalAction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ApprovalHistoryResponse {
    private UUID id;
    private MarketingApprovalAction action;
    private String comment;
    private String actionBy;
    private LocalDateTime actionAt;
}
