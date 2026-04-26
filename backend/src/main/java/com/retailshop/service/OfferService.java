package com.retailshop.service;

import com.retailshop.dto.OfferRequest;
import com.retailshop.dto.OfferResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.Product;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OfferService {
    OfferResponse createOffer(OfferRequest request);
    OfferResponse updateOffer(UUID id, OfferRequest request);
    void deleteOffer(UUID id);
    PaginatedResponse<OfferResponse> getActiveOffers(Pageable pageable);
    BigDecimal calculateBestDiscount(Product product, int quantity);
    String buildOfferMarketingMessage(OfferResponse offer);
}
