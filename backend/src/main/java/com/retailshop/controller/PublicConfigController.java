package com.retailshop.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PublicConfigController {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/env-config.js", produces = "application/javascript")
    public String envConfig() throws JsonProcessingException {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("API_BASE_URL", "/api");
        config.put("GOOGLE_MAPS_API_KEY", appProperties.getGoogleMaps().getApiKey());
        return "window.__APP_CONFIG__ = " + objectMapper.writeValueAsString(config) + ";";
    }
}
