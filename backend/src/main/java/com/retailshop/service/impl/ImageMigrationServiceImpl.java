package com.retailshop.service.impl;

import com.retailshop.dto.ImageMigrationResponse;
import com.retailshop.entity.Product;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.ImageMigrationService;
import com.retailshop.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ImageMigrationServiceImpl implements ImageMigrationService {

    private static final String DATA_IMAGE_PREFIX = "data:image/";

    private final ImageUploadService imageUploadService;
    private final ProductRepository productRepository;
    private final ReceiptSettingsRepository receiptSettingsRepository;

    @Override
    @Transactional
    public ImageMigrationResponse migrateDatabaseImagesToS3() {
        int productsScanned = 0;
        int productImagesMigrated = 0;
        int receiptSettingsScanned = 0;
        int receiptImagesMigrated = 0;
        int skipped = 0;

        List<Product> products = productRepository.findAll();
        productsScanned = products.size();
        for (Product product : products) {
            Optional<ParsedDataImage> parsedImage = parseDataImage(product.getImageDataUrl());
            if (parsedImage.isEmpty()) {
                skipped++;
                continue;
            }
            product.setImageDataUrl(upload(parsedImage.get(), product.getCategory()));
            productImagesMigrated++;
        }
        if (productImagesMigrated > 0) {
            productRepository.saveAll(products);
        }

        List<ReceiptSettings> settingsRows = receiptSettingsRepository.findAll();
        receiptSettingsScanned = settingsRows.size();
        for (ReceiptSettings settings : settingsRows) {
            receiptImagesMigrated += migrateReceiptImage(settings::getLogoUrl, settings::setLogoUrl, "branding");
            receiptImagesMigrated += migrateReceiptImage(settings::getHeroPrimaryImageUrl, settings::setHeroPrimaryImageUrl, "branding");
            receiptImagesMigrated += migrateReceiptImage(settings::getHeroSecondaryImageUrl, settings::setHeroSecondaryImageUrl, "branding");
        }
        if (receiptImagesMigrated > 0) {
            receiptSettingsRepository.saveAll(settingsRows);
        }

        return ImageMigrationResponse.builder()
                .productsScanned(productsScanned)
                .productImagesMigrated(productImagesMigrated)
                .receiptSettingsScanned(receiptSettingsScanned)
                .receiptImagesMigrated(receiptImagesMigrated)
                .skipped(skipped)
                .build();
    }

    private int migrateReceiptImage(Supplier<String> getter, Consumer<String> setter, String category) {
        Optional<ParsedDataImage> parsedImage = parseDataImage(getter.get());
        if (parsedImage.isEmpty()) {
            return 0;
        }
        setter.accept(upload(parsedImage.get(), category));
        return 1;
    }

    private String upload(ParsedDataImage parsedImage, String category) {
        return imageUploadService.uploadImageBytes(parsedImage.bytes(), parsedImage.contentType(), category)
                .getCloudfrontUrl();
    }

    private Optional<ParsedDataImage> parseDataImage(String value) {
        if (value == null || !value.toLowerCase(Locale.ROOT).startsWith(DATA_IMAGE_PREFIX)) {
            return Optional.empty();
        }

        int commaIndex = value.indexOf(',');
        int separatorIndex = value.indexOf(';');
        if (commaIndex < 0 || separatorIndex < 0 || separatorIndex > commaIndex) {
            return Optional.empty();
        }

        String contentType = value.substring("data:".length(), separatorIndex).toLowerCase(Locale.ROOT);
        byte[] bytes = Base64.getDecoder().decode(value.substring(commaIndex + 1));
        return Optional.of(new ParsedDataImage(contentType, bytes));
    }

    private record ParsedDataImage(String contentType, byte[] bytes) {
    }
}
