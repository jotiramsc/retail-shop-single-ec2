package com.retailshop.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.StaffUser;
import com.retailshop.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StaffJwtService {

    private static final String TOKEN_TYPE = "staff";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public StaffToken issueToken(StaffUser user) {
        Instant expiresAt = Instant.now().plusSeconds(appProperties.getStaffAuth().getJwtTtlMinutes() * 60);
        String header = base64Url(writeJson(Map.of("alg", "HS256", "typ", "JWT")));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("typ", TOKEN_TYPE);
        payload.put("sub", user.getUsername());
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedPayload = base64Url(writeJson(payload));
        String unsignedToken = header + "." + encodedPayload;
        return new StaffToken(unsignedToken + "." + sign(unsignedToken), expiresAt);
    }

    public Optional<String> parseUsername(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !signatureMatches(parts[0] + "." + parts[1], parts[2])) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!TOKEN_TYPE.equals(payload.path("typ").asText())) {
                return Optional.empty();
            }
            long expiresAtEpochSecond = payload.path("exp").asLong(0);
            if (Instant.now().getEpochSecond() >= expiresAtEpochSecond) {
                return Optional.empty();
            }
            String username = payload.path("sub").asText("");
            return username.isBlank() ? Optional.empty() : Optional.of(username);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private boolean signatureMatches(String unsignedToken, String signature) {
        byte[] expected = sign(unsignedToken).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appProperties.getStaffAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("Unable to sign staff token");
        }
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("Unable to create staff token");
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public record StaffToken(String token, Instant expiresAt) {
    }
}
