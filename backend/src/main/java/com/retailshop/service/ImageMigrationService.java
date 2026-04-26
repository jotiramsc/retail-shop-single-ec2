package com.retailshop.service;

import com.retailshop.dto.ImageMigrationResponse;

public interface ImageMigrationService {
    ImageMigrationResponse migrateDatabaseImagesToS3();
}
