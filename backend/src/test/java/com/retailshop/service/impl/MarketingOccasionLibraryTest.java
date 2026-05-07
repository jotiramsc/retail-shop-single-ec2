package com.retailshop.service.impl;

import com.retailshop.dto.MarketingCampaignSuggestionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingOccasionLibraryTest {

    @Test
    void shouldSuggestUpcomingAndSeasonalCampaignsForEarlyMayWindow() {
        List<MarketingCampaignSuggestionResponse> suggestions = MarketingOccasionLibrary.buildSuggestions(LocalDate.of(2026, 5, 1), 20, "https://kpskrishnai.com");

        assertTrue(suggestions.stream().anyMatch(entry -> "महाराष्ट्र दिन".equals(entry.getOccasionName())));
        assertTrue(suggestions.stream().anyMatch(entry -> "मातृ दिन".equals(entry.getOccasionName())));
        assertTrue(suggestions.stream().anyMatch(entry -> "लग्नाचा सीझन".equals(entry.getOccasionName())));
        assertTrue(suggestions.stream().anyMatch(entry -> "दुकान वर्धापन दिन".equals(entry.getOccasionName())));
    }

    @Test
    void shouldDetectNewOccasionsFromCampaignText() {
        var mothersDay = MarketingOccasionLibrary.detectOccasion("मातृ दिनासाठी खास ऑफर", "", "");
        var storeAnniversary = MarketingOccasionLibrary.detectOccasion("दुकान वर्धापन दिन मेगा ऑफर", "", "");

        assertNotNull(mothersDay);
        assertTrue(mothersDay.marathiName().contains("मातृ"));
        assertNotNull(storeAnniversary);
        assertFalse(storeAnniversary.marathiHashtag().isBlank());
    }
}
