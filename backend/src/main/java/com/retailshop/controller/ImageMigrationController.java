package com.retailshop.controller;

import com.retailshop.dto.ImageMigrationResponse;
import com.retailshop.service.ImageMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageMigrationController {

    private final ImageMigrationService imageMigrationService;

    @PostMapping("/migrate-db-to-s3")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS')")
    public ImageMigrationResponse migrateDatabaseImagesToS3() {
        return imageMigrationService.migrateDatabaseImagesToS3();
    }
}
