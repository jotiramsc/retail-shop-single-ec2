package com.retailshop.controller;

import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_PRODUCTS', 'PERM_OFFERS', 'PERM_RECEIPT_SETTINGS')")
    public ImageUploadResponse uploadImage(@RequestParam("image") MultipartFile image,
                                           @RequestParam("category") String category) {
        return imageUploadService.uploadImage(image, category);
    }
}
