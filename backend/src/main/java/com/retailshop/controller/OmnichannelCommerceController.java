package com.retailshop.controller;

import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.dto.OmnichannelLeadResponse;
import com.retailshop.dto.OmnichannelProductSearchRequest;
import com.retailshop.dto.OmnichannelProductSearchResponse;
import com.retailshop.dto.OmnichannelWebhookResponse;
import com.retailshop.service.OmnichannelCommerceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/omnichannel")
@RequiredArgsConstructor
public class OmnichannelCommerceController {

    private final OmnichannelCommerceService omnichannelCommerceService;

    @PostMapping("/leads")
    public OmnichannelLeadResponse captureLead(@Valid @RequestBody OmnichannelLeadRequest request) {
        return omnichannelCommerceService.captureLead(request);
    }

    @PostMapping("/products/search")
    public OmnichannelProductSearchResponse searchProducts(@RequestBody OmnichannelProductSearchRequest request) {
        return omnichannelCommerceService.searchProducts(request == null ? new OmnichannelProductSearchRequest() : request);
    }

    @GetMapping("/products/search")
    public OmnichannelProductSearchResponse searchProductsGet(@RequestParam(required = false) String q,
                                                              @RequestParam(required = false) String category,
                                                              @RequestParam(required = false) String occasion,
                                                              @RequestParam(required = false) BigDecimal minPrice,
                                                              @RequestParam(required = false) BigDecimal maxPrice,
                                                              @RequestParam(required = false) String source,
                                                              @RequestParam(required = false) String campaign,
                                                              @RequestParam(required = false) String coupon,
                                                              @RequestParam(required = false) Integer limit) {
        OmnichannelProductSearchRequest request = new OmnichannelProductSearchRequest();
        request.setQuery(q);
        request.setCategory(category);
        request.setOccasion(occasion);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setSource(source);
        request.setCampaign(campaign);
        request.setCouponCode(coupon);
        request.setLimit(limit);
        return omnichannelCommerceService.searchProducts(request);
    }

    @GetMapping(value = "/webhooks/meta", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyMetaWebhook(@RequestParam(name = "hub.mode", required = false) String mode,
                                                    @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
                                                    @RequestParam(name = "hub.challenge", required = false) String challenge) {
        return ResponseEntity.ok(omnichannelCommerceService.verifyMetaWebhook(mode, verifyToken, challenge));
    }

    @PostMapping("/webhooks/meta")
    public OmnichannelWebhookResponse receiveMetaWebhook(@RequestBody(required = false) String payload,
                                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        return omnichannelCommerceService.receiveMetaWebhook(payload == null ? "{}" : payload, signature);
    }
}
