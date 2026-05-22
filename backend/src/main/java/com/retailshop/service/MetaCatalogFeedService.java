package com.retailshop.service;

import com.retailshop.dto.FacebookFeedPreviewResponse;
import com.retailshop.dto.FacebookFeedTokenResponse;

public interface MetaCatalogFeedService {
    FacebookFeedTokenResponse generateFeedToken();

    FacebookFeedPreviewResponse previewFeed();

    String xmlFeed(String token);

    String csvFeed(String token);
}
