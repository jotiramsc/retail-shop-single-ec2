package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ImageUploadResponse {
    private UUID id;
    private String category;
    private String cloudfrontUrl;
    private String s3Key;
    private LocalDateTime createdAt;
}
