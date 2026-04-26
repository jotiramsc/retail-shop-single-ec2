package com.retailshop.controller;

import com.retailshop.dto.ReceiptSettingsRequest;
import com.retailshop.dto.ReceiptSettingsResponse;
import com.retailshop.service.ReceiptSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/receipt")
@RequiredArgsConstructor
public class ReceiptSettingsController {

    private final ReceiptSettingsService receiptSettingsService;

    @GetMapping
    public ReceiptSettingsResponse getSettings() {
        return receiptSettingsService.getSettings();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_RECEIPT_SETTINGS')")
    @ResponseStatus(HttpStatus.OK)
    public ReceiptSettingsResponse updateSettings(@Valid @RequestBody ReceiptSettingsRequest request) {
        return receiptSettingsService.updateSettings(request);
    }
}
