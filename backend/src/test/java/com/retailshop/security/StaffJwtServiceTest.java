package com.retailshop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.StaffUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffJwtServiceTest {

    @Test
    void shouldIssueAndParseStaffToken() {
        StaffJwtService service = serviceWithTtl(5);
        StaffUser user = new StaffUser();
        user.setUsername("admin");

        StaffJwtService.StaffToken token = service.issueToken(user);

        assertEquals(3, token.token().split("\\.").length);
        assertTrue(token.expiresAt().isAfter(Instant.now()));
        assertEquals("admin", service.parseUsername(token.token()).orElseThrow());
    }

    @Test
    void shouldRejectTamperedOrExpiredToken() {
        StaffJwtService service = serviceWithTtl(-1);
        StaffUser user = new StaffUser();
        user.setUsername("admin");

        StaffJwtService.StaffToken token = service.issueToken(user);

        assertTrue(service.parseUsername(token.token()).isEmpty());
        assertTrue(serviceWithTtl(5).parseUsername(token.token() + "x").isEmpty());
    }

    private StaffJwtService serviceWithTtl(long ttlMinutes) {
        AppProperties properties = new AppProperties();
        properties.getStaffAuth().setJwtSecret("test-staff-secret");
        properties.getStaffAuth().setJwtTtlMinutes(ttlMinutes);
        return new StaffJwtService(properties, new ObjectMapper());
    }
}
