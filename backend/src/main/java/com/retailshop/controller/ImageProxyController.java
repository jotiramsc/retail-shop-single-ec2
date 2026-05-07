package com.retailshop.controller;

import com.retailshop.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class ImageProxyController {

    private static final String IMAGE_PREFIX = "/api/images/";

    private final AppProperties appProperties;
    private final S3Client s3Client;

    @GetMapping("/api/images/**")
    public ResponseEntity<byte[]> getImage(HttpServletRequest request) {
        String key = extractImageKey(request);
        validateImageKey(key);

        if (isBlank(appProperties.getAws().getS3Bucket())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }

        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3Bucket())
                    .key(key)
                    .build());

            MediaType contentType = resolveContentType(object.response().contentType());
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(object.asByteArray());
        } catch (NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found", ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load image", ex);
        }
    }

    private String extractImageKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int prefixIndex = uri.indexOf(IMAGE_PREFIX);
        if (prefixIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path");
        }
        String encodedKey = uri.substring(prefixIndex + IMAGE_PREFIX.length());
        try {
            return URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path", ex);
        }
    }

    private void validateImageKey(String key) {
        if (isBlank(key)
                || key.startsWith("/")
                || key.startsWith("\\")
                || key.contains("..")
                || key.contains("\\")
                || !isSupportedImageType(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path");
        }
    }

    private boolean isSupportedImageType(String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return lowerKey.endsWith(".jpg")
                || lowerKey.endsWith(".jpeg")
                || lowerKey.endsWith(".png")
                || lowerKey.endsWith(".webp");
    }

    private MediaType resolveContentType(String contentType) {
        if (isBlank(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
