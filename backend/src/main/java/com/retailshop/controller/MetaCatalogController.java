package com.retailshop.controller;

import com.retailshop.dto.FacebookFeedPreviewResponse;
import com.retailshop.dto.FacebookFeedTokenResponse;
import com.retailshop.service.MetaCatalogFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MetaCatalogController {

    private final MetaCatalogFeedService metaCatalogFeedService;

    @PostMapping("/admin/brand-config/facebook-feed-token/generate")
    public FacebookFeedTokenResponse generateToken() {
        return metaCatalogFeedService.generateFeedToken();
    }

    @GetMapping("/admin/brand-config/facebook-feed-preview")
    public FacebookFeedPreviewResponse preview() {
        return metaCatalogFeedService.previewFeed();
    }

    @GetMapping(value = "/meta/catalog-feed.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> xmlFeed(@RequestParam(required = false) String token) {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(metaCatalogFeedService.xmlFeed(token));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(exception.getReason());
        }
    }

    @GetMapping(value = "/meta/catalog-feed.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> csvFeed(@RequestParam(required = false) String token) {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"krishnai-facebook-catalog.csv\"")
                    .body(metaCatalogFeedService.csvFeed(token));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(exception.getReason());
        }
    }
}
