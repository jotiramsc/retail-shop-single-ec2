package com.retailshop.service;

import java.util.UUID;

public interface ProductAiDescriptionService {

    void queueDescriptionGeneration(UUID productId);
}
