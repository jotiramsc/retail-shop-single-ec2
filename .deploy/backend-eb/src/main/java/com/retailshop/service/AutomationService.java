package com.retailshop.service;

import com.retailshop.dto.OfferSuggestionResponse;

import java.util.List;

public interface AutomationService {
    List<OfferSuggestionResponse> suggestOffersForSlowMovingProducts();
    void distributeOfferAnnouncement(String message);
}
