package com.retailshop.service;

import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.dto.OmnichannelLeadResponse;
import com.retailshop.dto.OmnichannelProductSearchRequest;
import com.retailshop.dto.OmnichannelProductSearchResponse;
import com.retailshop.dto.OmnichannelWebhookResponse;

public interface OmnichannelCommerceService {
    OmnichannelLeadResponse captureLead(OmnichannelLeadRequest request);

    OmnichannelProductSearchResponse searchProducts(OmnichannelProductSearchRequest request);

    String verifyMetaWebhook(String mode, String verifyToken, String challenge);

    OmnichannelWebhookResponse receiveMetaWebhook(String payload, String signature);
}
