package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.dto.GeoLookupResult;
import com.retailshop.service.GeoLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class IpapiGeoLookupService implements GeoLookupService {

    private static final String LOOKUP_URL_TEMPLATE = "https://ipapi.co/%s/json/";
    private static final String REVERSE_LOOKUP_URL_TEMPLATE = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%s&lon=%s&zoom=18&addressdetails=1";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public IpapiGeoLookupService(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    IpapiGeoLookupService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public GeoLookupResult lookup(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(LOOKUP_URL_TEMPLATE, encode(ipAddress.trim()))))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .header("accept", "application/json")
                    .header("user-agent", "retail-shop-analytics/1.0")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return null;
            }

            JsonNode payload = objectMapper.readTree(response.body());
            if (payload.path("error").asBoolean(false)) {
                return null;
            }

            return GeoLookupResult.builder()
                    .ipAddress(text(payload, "ip"))
                    .city(text(payload, "city"))
                    .region(text(payload, "region"))
                    .countryName(text(payload, "country_name"))
                    .countryCode(text(payload, "country_code"))
                    .timezone(text(payload, "timezone"))
                    .latitude(number(payload, "latitude"))
                    .longitude(number(payload, "longitude"))
                    .organization(text(payload, "org"))
                    .build();
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    @Override
    public GeoLookupResult reverseLookup(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(
                            REVERSE_LOOKUP_URL_TEMPLATE,
                            encodeLatitudeLongitude(latitude),
                            encodeLatitudeLongitude(longitude)
                    )))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .header("accept", "application/json")
                    .header("user-agent", "retail-shop-analytics/1.0")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return null;
            }

            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode address = payload.path("address");

            String locality = firstNonBlank(
                    text(address, "suburb"),
                    text(address, "neighbourhood"),
                    text(address, "quarter"),
                    text(address, "city_district"),
                    text(address, "borough"),
                    text(address, "hamlet"),
                    text(address, "village"),
                    text(address, "municipality")
            );
            String city = firstNonBlank(
                    text(address, "city"),
                    text(address, "town"),
                    text(address, "village"),
                    text(address, "municipality"),
                    text(address, "county")
            );
            String region = firstNonBlank(
                    text(address, "state_district"),
                    text(address, "state"),
                    text(address, "region")
            );
            String countryName = text(address, "country");
            String countryCode = upper(text(address, "country_code"));
            String locationLabel = firstNonBlank(
                    compactLocation(locality, city, region, countryName),
                    text(payload, "display_name")
            );

            return GeoLookupResult.builder()
                    .city(firstNonBlank(city, locality))
                    .region(region)
                    .countryName(countryName)
                    .countryCode(countryCode)
                    .latitude(number(payload, "lat"))
                    .longitude(number(payload, "lon"))
                    .locationLabel(locationLabel)
                    .postalCode(text(address, "postcode"))
                    .build();
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String text(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        return value == null || value.isBlank() ? null : value;
    }

    private Double number(JsonNode payload, String field) {
        JsonNode node = payload.path(field);
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodeLatitudeLongitude(Double value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String compactLocation(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.toString().contains(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
