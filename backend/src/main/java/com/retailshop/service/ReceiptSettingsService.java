package com.retailshop.service;

import com.retailshop.dto.ReceiptSettingsRequest;
import com.retailshop.dto.ReceiptSettingsResponse;

public interface ReceiptSettingsService {
    ReceiptSettingsResponse getSettings();
    ReceiptSettingsResponse updateSettings(ReceiptSettingsRequest request);
}
