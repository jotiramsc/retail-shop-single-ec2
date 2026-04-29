package com.retailshop.service.impl;

import com.retailshop.dto.SiteVisitRequest;
import com.retailshop.entity.SiteVisit;
import com.retailshop.enums.SiteVisitSourceType;
import com.retailshop.repository.SiteVisitRepository;
import com.retailshop.service.GeoLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteInteractionServiceImplTest {

    @Mock
    private SiteVisitRepository siteVisitRepository;

    @Mock
    private GeoLookupService geoLookupService;

    @InjectMocks
    private SiteInteractionServiceImpl siteInteractionService;

    @Test
    void shouldRecordCampaignVisitAndReturnUpdatedSummary() {
        SiteVisitRequest request = new SiteVisitRequest();
        request.setVisitorId("visitor-1");
        request.setPath("/");
        request.setReferrer("https://www.instagram.com/reel/demo");
        request.setUtmSource("meta-ads");
        request.setUtmCampaign("akshaya-tritiya");

        when(siteVisitRepository.findByVisitorIdAndVisitDate(eq("visitor-1"), any(LocalDate.class))).thenReturn(Optional.empty());
        when(siteVisitRepository.save(any(SiteVisit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(siteVisitRepository.count()).thenReturn(14L);
        when(geoLookupService.lookup("8.8.8.8")).thenReturn(com.retailshop.dto.GeoLookupResult.builder()
                .ipAddress("8.8.8.8")
                .city("Mumbai")
                .region("Maharashtra")
                .countryName("India")
                .countryCode("IN")
                .timezone("Asia/Kolkata")
                .organization("Google LLC")
                .latitude(19.08)
                .longitude(72.88)
                .build());

        var response = siteInteractionService.recordVisit(request, "Mozilla/5.0", "en-IN", "8.8.8.8");

        assertTrue(response.isRecorded());
        assertEquals(14L, response.getTotalVisits());

        ArgumentCaptor<SiteVisit> visitCaptor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(siteVisitRepository).save(visitCaptor.capture());
        SiteVisit savedVisit = visitCaptor.getValue();
        assertEquals("/", savedVisit.getLandingPath());
        assertEquals(SiteVisitSourceType.CAMPAIGN, savedVisit.getSourceType());
        assertEquals("meta-ads", savedVisit.getSourceLabel());
        assertEquals("www.instagram.com", savedVisit.getReferrerHost());
        assertEquals("8.8.8.8", savedVisit.getIpAddress());
        assertEquals("Mumbai", savedVisit.getCity());
        assertEquals("India", savedVisit.getCountryName());
        assertEquals("IP", savedVisit.getLocationSource());
    }

    @Test
    void shouldSkipDuplicateVisitForSameVisitorAndDay() {
        SiteVisit existing = new SiteVisit();
        existing.setVisitorId("visitor-2");
        existing.setVisitDate(LocalDate.now());
        existing.setAcceptLanguage("en-IN");
        existing.setUserAgent("Mozilla/5.0");
        existing.setIpAddress("8.8.8.8");
        existing.setLocationSource("IP");

        SiteVisitRequest request = new SiteVisitRequest();
        request.setVisitorId("visitor-2");
        request.setPath("/products");

        when(siteVisitRepository.findByVisitorIdAndVisitDate(eq("visitor-2"), any(LocalDate.class))).thenReturn(Optional.of(existing));
        when(siteVisitRepository.count()).thenReturn(21L);

        var response = siteInteractionService.recordVisit(request, "Mozilla/5.0", "en-IN", "8.8.8.8");

        assertFalse(response.isRecorded());
        assertEquals(21L, response.getTotalVisits());
        verify(siteVisitRepository, never()).save(any(SiteVisit.class));
    }

    @Test
    void shouldEnrichExistingVisitWithBrowserCoordinatesWithoutDoubleCounting() {
        SiteVisit existing = new SiteVisit();
        existing.setVisitorId("visitor-2");
        existing.setVisitDate(LocalDate.now());
        existing.setIpAddress("8.8.8.8");
        existing.setCity("Pune");
        existing.setRegion("Maharashtra");
        existing.setCountryName("India");

        SiteVisitRequest request = new SiteVisitRequest();
        request.setVisitorId("visitor-2");
        request.setLatitude(18.5092);
        request.setLongitude(73.8328);
        request.setAccuracyMeters(42.0);
        request.setTimezone("Asia/Kolkata");

        when(siteVisitRepository.findByVisitorIdAndVisitDate(eq("visitor-2"), any(LocalDate.class))).thenReturn(Optional.of(existing));
        when(siteVisitRepository.count()).thenReturn(21L);
        when(geoLookupService.reverseLookup(18.5092, 73.8328)).thenReturn(com.retailshop.dto.GeoLookupResult.builder()
                .city("Pune")
                .region("Maharashtra")
                .countryName("India")
                .countryCode("IN")
                .latitude(18.5092)
                .longitude(73.8328)
                .locationLabel("Erandwane, Pune, Maharashtra, India")
                .postalCode("411004")
                .build());

        var response = siteInteractionService.recordVisit(request, "Mozilla/5.0", "en-IN", "8.8.8.8");

        assertFalse(response.isRecorded());
        assertEquals(21L, response.getTotalVisits());

        ArgumentCaptor<SiteVisit> visitCaptor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(siteVisitRepository).save(visitCaptor.capture());
        SiteVisit savedVisit = visitCaptor.getValue();
        assertEquals("Erandwane, Pune, Maharashtra, India", savedVisit.getExactLocationName());
        assertEquals("411004", savedVisit.getPostalCode());
        assertEquals("BROWSER", savedVisit.getLocationSource());
        assertEquals(18.5092, savedVisit.getLatitude());
        assertEquals(73.8328, savedVisit.getLongitude());
        assertEquals(42.0, savedVisit.getLocationAccuracyMeters());
    }

    @Test
    void shouldBuildReportWithSourceAndLandingBreakdowns() {
        when(siteVisitRepository.count()).thenReturn(45L);
        when(siteVisitRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(12L);
        when(siteVisitRepository.countDailyBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                daily(LocalDate.of(2026, 4, 27), 5L),
                daily(LocalDate.of(2026, 4, 28), 7L)
        ));
        when(siteVisitRepository.summarizeSources(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                source("DIRECT", "Direct", 4L),
                source("SEARCH", "google.com", 3L),
                source("SOCIAL", "instagram.com", 2L),
                source("CAMPAIGN", "meta-ads", 3L)
        ));
        when(siteVisitRepository.summarizeReferrers(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                label("Direct", 4L),
                label("google.com", 3L)
        ));
        when(siteVisitRepository.summarizeCountries(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                country("India", "IN", 8L),
                country("United States", "US", 4L)
        ));
        when(siteVisitRepository.summarizeLandingPages(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                label("/", 8L),
                label("/products", 4L)
        ));
        when(siteVisitRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(recentVisit(), secondRecentVisit()));

        var report = siteInteractionService.getReport(30);

        assertEquals(45L, report.getTotalVisits());
        assertEquals(12L, report.getVisitsInRange());
        assertEquals(4L, report.getDirectVisits());
        assertEquals(3L, report.getSearchVisits());
        assertEquals(2L, report.getSocialVisits());
        assertEquals(3L, report.getCampaignVisits());
        assertEquals("India", report.getTopCountries().get(0).getCountryName());
        assertEquals("/", report.getTopLandingPages().get(0).getLabel());
        assertEquals("google.com", report.getTopReferrers().get(1).getLabel());
        assertEquals("/products", report.getRecentVisits().get(0).getLandingPath());
        assertEquals(2, report.getMapPoints().get(0).getVisits());
        assertEquals("Erandwane, Pune, Maharashtra, India", report.getMapPoints().get(0).getLocationName());
    }

    private SiteVisitRepository.DailyVisitProjection daily(LocalDate date, Long visits) {
        return new SiteVisitRepository.DailyVisitProjection() {
            @Override
            public LocalDate getVisitDate() {
                return date;
            }

            @Override
            public Long getVisits() {
                return visits;
            }
        };
    }

    private SiteVisitRepository.SourceBreakdownProjection source(String sourceType, String sourceLabel, Long visits) {
        return new SiteVisitRepository.SourceBreakdownProjection() {
            @Override
            public String getSourceType() {
                return sourceType;
            }

            @Override
            public String getSourceLabel() {
                return sourceLabel;
            }

            @Override
            public Long getVisits() {
                return visits;
            }
        };
    }

    private SiteVisitRepository.LabelCountProjection label(String label, Long visits) {
        return new SiteVisitRepository.LabelCountProjection() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public Long getVisits() {
                return visits;
            }
        };
    }

    private SiteVisitRepository.CountryBreakdownProjection country(String countryName, String countryCode, Long visits) {
        return new SiteVisitRepository.CountryBreakdownProjection() {
            @Override
            public String getCountryName() {
                return countryName;
            }

            @Override
            public String getCountryCode() {
                return countryCode;
            }

            @Override
            public Long getVisits() {
                return visits;
            }
        };
    }

    private SiteVisit recentVisit() {
        SiteVisit visit = new SiteVisit();
        visit.setLandingPath("/products");
        visit.setCreatedAt(LocalDateTime.of(2026, 4, 28, 18, 45));
        visit.setSourceType(SiteVisitSourceType.SOCIAL);
        visit.setSourceLabel("instagram.com");
        visit.setIpAddress("8.8.8.8");
        visit.setCity("Mumbai");
        visit.setRegion("Maharashtra");
        visit.setCountryName("India");
        visit.setCountryCode("IN");
        visit.setTimezone("Asia/Kolkata");
        visit.setExactLocationName("Erandwane, Pune, Maharashtra, India");
        visit.setPostalCode("411004");
        visit.setLocationSource("BROWSER");
        visit.setLatitude(19.08);
        visit.setLongitude(72.88);
        visit.setLocationAccuracyMeters(35.0);
        visit.setOrganization("Google LLC");
        visit.setReferrerHost("instagram.com");
        visit.setUtmSource("meta-ads");
        visit.setUtmCampaign("festive-drop");
        visit.setAcceptLanguage("en-IN");
        return visit;
    }

    private SiteVisit secondRecentVisit() {
        SiteVisit visit = new SiteVisit();
        visit.setLandingPath("/");
        visit.setCreatedAt(LocalDateTime.of(2026, 4, 28, 17, 10));
        visit.setSourceType(SiteVisitSourceType.SEARCH);
        visit.setSourceLabel("www.google.com");
        visit.setIpAddress("1.1.1.1");
        visit.setCity("Pune");
        visit.setRegion("Maharashtra");
        visit.setCountryName("India");
        visit.setCountryCode("IN");
        visit.setTimezone("Asia/Kolkata");
        visit.setExactLocationName("Erandwane, Pune, Maharashtra, India");
        visit.setPostalCode("411004");
        visit.setLocationSource("BROWSER");
        visit.setLatitude(18.50922);
        visit.setLongitude(73.83281);
        visit.setLocationAccuracyMeters(28.0);
        visit.setOrganization("Reliance Jio");
        visit.setReferrerHost("www.google.com");
        visit.setAcceptLanguage("en-IN");
        return visit;
    }
}
