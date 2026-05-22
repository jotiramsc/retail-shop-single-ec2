package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerResponse {
    private UUID id;
    private String name;
    private String mobile;
    private String email;
    private LocalDate dateOfBirth;
    private LocalDate anniversaryDate;
    private String customerTags;
    private List<String> segments;
    private LocalDateTime createdAt;
}
