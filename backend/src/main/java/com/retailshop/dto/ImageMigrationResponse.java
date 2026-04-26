package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageMigrationResponse {
    private int productsScanned;
    private int productImagesMigrated;
    private int receiptSettingsScanned;
    private int receiptImagesMigrated;
    private int skipped;
}
