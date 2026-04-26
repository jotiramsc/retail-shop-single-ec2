package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.entity.ImageAsset;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.ImageAssetRepository;
import com.retailshop.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ImageUploadService implements ImageUploadService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final AppProperties appProperties;
    private final ImageAssetRepository imageAssetRepository;
    private final S3Client s3Client;

    @Override
    @Transactional
    public ImageUploadResponse uploadImage(MultipartFile image, String category) {
        validateConfigured();
        validateImage(image);

        try {
            return uploadValidatedBytes(image.getInputStream().readAllBytes(), image.getContentType(), image.getSize(), category);
        } catch (IOException ex) {
            throw new BusinessException("Unable to read uploaded image");
        }
    }

    @Override
    @Transactional
    public ImageUploadResponse uploadImageBytes(byte[] imageBytes, String contentType, String category) {
        validateConfigured();
        validateImageBytes(imageBytes, contentType);
        return uploadValidatedBytes(imageBytes, contentType, imageBytes.length, category);
    }

    private ImageUploadResponse uploadValidatedBytes(byte[] imageBytes, String contentType, long size, String category) {
        String normalizedCategory = normalizeCategory(category);
        String fileName = UUID.randomUUID() + "-" + Instant.now().toEpochMilli() + "." + EXTENSIONS.get(contentType);
        String s3Key = normalizedCategory + "/" + fileName;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3Bucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(new ByteArrayInputStream(imageBytes), size));
        } catch (RuntimeException ex) {
            throw new BusinessException("Unable to upload image to S3: " + ex.getMessage());
        }

        ImageAsset asset = new ImageAsset();
        asset.setCategory(normalizedCategory);
        asset.setCloudfrontUrl(buildCloudFrontUrl(s3Key));
        asset.setS3Key(s3Key);
        asset.setContentType(contentType);
        asset.setFileSizeBytes(size);

        return mapToResponse(imageAssetRepository.save(asset));
    }

    private void validateConfigured() {
        if (isBlank(appProperties.getAws().getS3Bucket()) || isBlank(appProperties.getAws().getCloudfrontDomain())) {
            throw new BusinessException("Image upload is not configured. Set AWS_S3_BUCKET and AWS_CLOUDFRONT_DOMAIN.");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException("Image file is required");
        }
        if (image.getSize() > appProperties.getAws().getUploadMaxBytes()) {
            throw new BusinessException("Image exceeds max upload size");
        }
        if (!ALLOWED_TYPES.contains(image.getContentType())) {
            throw new BusinessException("Only JPG, PNG, and WEBP images are allowed");
        }
    }

    private void validateImageBytes(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("Image file is required");
        }
        if (imageBytes.length > appProperties.getAws().getUploadMaxBytes()) {
            throw new BusinessException("Image exceeds max upload size");
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Only JPG, PNG, and WEBP images are allowed");
        }
    }

    private String normalizeCategory(String category) {
        String normalized = isBlank(category) ? "general" : category.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-|-$", "");
        return normalized.isBlank() ? "general" : normalized;
    }

    private String buildCloudFrontUrl(String s3Key) {
        String domain = appProperties.getAws().getCloudfrontDomain().trim();
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        return domain.replaceAll("/+$", "") + "/" + s3Key;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ImageUploadResponse mapToResponse(ImageAsset asset) {
        return ImageUploadResponse.builder()
                .id(asset.getId())
                .category(asset.getCategory())
                .cloudfrontUrl(asset.getCloudfrontUrl())
                .s3Key(asset.getS3Key())
                .createdAt(asset.getCreatedAt())
                .build();
    }
}
