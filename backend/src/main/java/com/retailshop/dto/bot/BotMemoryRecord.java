package com.retailshop.dto.bot;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
public class BotMemoryRecord {
    private String id;
    private String customerId;
    private String mobile;
    private String memoryType;
    private LocalDateTime createdAt;
    private String source;
    private String summaryText;
    private List<String> tags;
    private Map<String, String> metadata;
    private double score;
}
