package com.retailshop.service;

import com.retailshop.dto.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImageUploadService {
    ImageUploadResponse uploadImage(MultipartFile image, String category);

    ImageUploadResponse uploadImageBytes(byte[] imageBytes, String contentType, String category);
}
