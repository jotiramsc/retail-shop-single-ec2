package com.retailshop.security;

import com.retailshop.config.AppProperties;
import com.retailshop.entity.Customer;
import com.retailshop.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerJwtService {

    private final AppProperties appProperties;

    public String issueToken(Customer customer) {
        Instant expiresAt = Instant.now().plusSeconds(appProperties.getCustomerAuth().getJwtTtlMinutes() * 60);
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + customer.getId() + "\",\"mobile\":\"" + customer.getMobile()
                + "\",\"exp\":" + expiresAt.getEpochSecond() + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public Optional<CustomerPrincipal> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !sign(parts[0] + "." + parts[1]).equals(parts[2])) {
                return Optional.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String sub = extractString(payload, "sub");
            String mobile = extractString(payload, "mobile");
            long exp = Long.parseLong(extractNumber(payload, "exp"));
            if (Instant.now().getEpochSecond() > exp) {
                return Optional.empty();
            }
            return Optional.of(new CustomerPrincipal(UUID.fromString(sub), mobile));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appProperties.getCustomerAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("Unable to sign customer token");
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String extractString(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IllegalArgumentException("Missing field");
        }
        start += pattern.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private String extractNumber(String json, String field) {
        String pattern = "\"" + field + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IllegalArgumentException("Missing field");
        }
        start += pattern.length();
        int end = json.indexOf(',', start);
        return json.substring(start, end < 0 ? json.indexOf('}', start) : end);
    }
}
