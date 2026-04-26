package com.retailshop.service;

import com.retailshop.dto.OfferRequest;
import com.retailshop.dto.OfferResponse;
import com.retailshop.entity.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OfferService {
    OfferResponse createOffer(OfferRequest request);
    OfferResponse updateOffer(UUID id, OfferRequest request);
    void deleteOffer(UUID id);
    List<OfferResponse> getActiveOffers();
    BigDecimal calculateBestDiscount(Product product, int quantity);
    String buildOfferMarketingMessage(OfferResponse offer);
}
