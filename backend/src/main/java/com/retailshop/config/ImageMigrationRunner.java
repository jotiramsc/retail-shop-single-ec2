package com.retailshop.config;

import com.retailshop.dto.ImageMigrationResponse;
import com.retailshop.service.ImageMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageMigrationRunner implements ApplicationRunner {

    private final AppProperties appProperties;
    private final ConfigurableApplicationContext context;
    private final ImageMigrationService imageMigrationService;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getAws().isMigrateImagesOnStart()) {
            return;
        }

        ImageMigrationResponse response = imageMigrationService.migrateDatabaseImagesToS3();
        log.info("Database image migration completed: productsScanned={}, productImagesMigrated={}, receiptSettingsScanned={}, receiptImagesMigrated={}, skipped={}",
                response.getProductsScanned(),
                response.getProductImagesMigrated(),
                response.getReceiptSettingsScanned(),
                response.getReceiptImagesMigrated(),
                response.getSkipped());

        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
