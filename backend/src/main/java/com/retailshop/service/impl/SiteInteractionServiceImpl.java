package com.retailshop.service.impl;

import com.retailshop.dto.SiteInteractionDailyResponse;
import com.retailshop.dto.SiteInteractionLabelCountResponse;
import com.retailshop.dto.SiteInteractionMapPointResponse;
import com.retailshop.dto.SiteInteractionRecentVisitResponse;
import com.retailshop.dto.SiteInteractionReportResponse;
import com.retailshop.dto.SiteInteractionCountryResponse;
import com.retailshop.dto.SiteInteractionSourceResponse;
import com.retailshop.dto.SiteInteractionSummaryResponse;
import com.retailshop.dto.SiteVisitRequest;
import com.retailshop.entity.SiteVisit;
import com.retailshop.enums.SiteVisitSourceType;
import com.retailshop.repository.SiteVisitRepository;
import com.retailshop.service.GeoLookupService;
import com.retailshop.service.SiteInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SiteInteractionServiceImpl implements SiteInteractionService {

    private static final Set<String> SEARCH_HOSTS = Set.of(
            "google.", "bing.", "yahoo.", "duckduckgo.", "ecosia.", "baidu."
    );
    private static final Set<String> SOCIAL_HOSTS = Set.of(
            "facebook.", "instagram.", "l.instagram.", "m.facebook.", "t.co", "twitter.", "x.com",
            "youtube.", "linkedin.", "pinterest.", "whatsapp.", "web.whatsapp.", "chat.whatsapp."
    );
    private static final int MAP_POINT_LIMIT = 120;

    private final SiteVisitRepository siteVisitRepository;
    private final GeoLookupService geoLookupService;

    @Override
    @Transactional
    public SiteInteractionSummaryResponse recordVisit(SiteVisitRequest request,
                                                      String userAgent,
                                                      String acceptLanguage,
                                                      String clientIpAddress) {
        LocalDate today = LocalDate.now();
        String visitorId = normalize(request != null ? request.getVisitorId() : null);
        if (visitorId == null) {
            return buildSummary(false);
        }

        SiteVisit existingVisit = siteVisitRepository.findByVisitorIdAndVisitDate(visitorId, today).orElse(null);
        if (existingVisit != null) {
            boolean updated = enrichExistingVisit(existingVisit, request, userAgent, acceptLanguage, clientIpAddress);
            if (updated) {
                siteVisitRepository.save(existingVisit);
            }
            return buildSummary(false);
        }

        SiteVisit visit = new SiteVisit();
        visit.setVisitorId(visitorId);
        visit.setVisitDate(today);
        visit.setLandingPath(resolveLandingPath(request != null ? request.getPath() : null));
        visit.setReferrer(truncate(request != null ? request.getReferrer() : null, 4000));
        visit.setReferrerHost(extractHost(request != null ? request.getReferrer() : null));
        visit.setUtmSource(truncate(normalize(request != null ? request.getUtmSource() : null), 255));
        visit.setUtmMedium(truncate(normalize(request != null ? request.getUtmMedium() : null), 255));
        visit.setUtmCampaign(truncate(normalize(request != null ? request.getUtmCampaign() : null), 255));
        visit.setSourceType(resolveSourceType(visit.getReferrerHost(), visit.getUtmSource(), visit.getUtmMedium(), visit.getUtmCampaign()));
        visit.setSourceLabel(resolveSourceLabel(visit.getSourceType(), visit.getReferrerHost(), visit.getUtmSource(), visit.getUtmCampaign(), visit.getUtmMedium()));
        visit.setAcceptLanguage(truncate(normalize(acceptLanguage), 255));
        visit.setUserAgent(truncate(normalize(userAgent), 1000));
        enrichWithGeoData(visit, request, clientIpAddress);

        try {
            siteVisitRepository.save(visit);
        } catch (DataIntegrityViolationException duplicate) {
            return buildSummary(false);
        }

        return buildSummary(true);
    }

    @Override
    @Transactional(readOnly = true)
    public SiteInteractionReportResponse getReport(int days) {
        LocalDate toDate = LocalDate.now();
        int safeDays = Math.min(Math.max(days, 1), 3650);
        LocalDate fromDate = toDate.minusDays(safeDays - 1L);
        LocalDateTime rangeStart = fromDate.atStartOfDay();
        LocalDateTime rangeEnd = toDate.plusDays(1L).atStartOfDay().minusNanos(1L);

        List<SiteInteractionSourceResponse> sourceBreakdown = siteVisitRepository.summarizeSources(fromDate, toDate).stream()
                .map(row -> SiteInteractionSourceResponse.builder()
                        .sourceType(row.getSourceType())
                        .sourceLabel(row.getSourceLabel())
                        .visits(defaultLong(row.getVisits()))
                        .build())
                .toList();

        long directVisits = totalForType(sourceBreakdown, SiteVisitSourceType.DIRECT);
        long searchVisits = totalForType(sourceBreakdown, SiteVisitSourceType.SEARCH);
        long socialVisits = totalForType(sourceBreakdown, SiteVisitSourceType.SOCIAL);
        long referralVisits = totalForType(sourceBreakdown, SiteVisitSourceType.REFERRAL);
        long campaignVisits = totalForType(sourceBreakdown, SiteVisitSourceType.CAMPAIGN);
        List<SiteVisit> visitsInRange = siteVisitRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(rangeStart, rangeEnd);

        return SiteInteractionReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalVisits(siteVisitRepository.count())
                .visitsInRange(siteVisitRepository.countByCreatedAtBetween(rangeStart, rangeEnd))
                .directVisits(directVisits)
                .searchVisits(searchVisits)
                .socialVisits(socialVisits)
                .referralVisits(referralVisits)
                .campaignVisits(campaignVisits)
                .dailyVisits(siteVisitRepository.countDailyBetween(fromDate, toDate).stream()
                        .map(row -> SiteInteractionDailyResponse.builder()
                                .date(row.getVisitDate())
                                .visits(defaultLong(row.getVisits()))
                                .build())
                        .toList())
                .sourceBreakdown(sourceBreakdown.stream().limit(12).toList())
                .topCountries(siteVisitRepository.summarizeCountries(fromDate, toDate).stream()
                        .map(row -> SiteInteractionCountryResponse.builder()
                                .countryName(row.getCountryName())
                                .countryCode(row.getCountryCode())
                                .visits(defaultLong(row.getVisits()))
                                .build())
                        .limit(8)
                        .toList())
                .topReferrers(siteVisitRepository.summarizeReferrers(fromDate, toDate).stream()
                        .map(row -> SiteInteractionLabelCountResponse.builder()
                                .label(row.getLabel())
                                .visits(defaultLong(row.getVisits()))
                                .build())
                        .limit(8)
                        .toList())
                .topLandingPages(siteVisitRepository.summarizeLandingPages(fromDate, toDate).stream()
                        .map(row -> SiteInteractionLabelCountResponse.builder()
                                .label(row.getLabel())
                                .visits(defaultLong(row.getVisits()))
                                .build())
                        .limit(8)
                        .toList())
                .mapPoints(buildMapPoints(visitsInRange))
                .recentVisits(visitsInRange.stream()
                        .limit(20)
                        .map(visit -> SiteInteractionRecentVisitResponse.builder()
                                .createdAt(visit.getCreatedAt())
                                .landingPath(visit.getLandingPath())
                                .sourceType(visit.getSourceType().name())
                                .sourceLabel(visit.getSourceLabel())
                                .ipAddress(visit.getIpAddress())
                                .city(visit.getCity())
                                .region(visit.getRegion())
                                .countryName(visit.getCountryName())
                                .countryCode(visit.getCountryCode())
                                .timezone(visit.getTimezone())
                                .exactLocationName(visit.getExactLocationName())
                                .postalCode(visit.getPostalCode())
                                .locationSource(visit.getLocationSource())
                                .latitude(visit.getLatitude())
                                .longitude(visit.getLongitude())
                                .locationAccuracyMeters(visit.getLocationAccuracyMeters())
                                .organization(visit.getOrganization())
                                .referrerHost(visit.getReferrerHost())
                                .utmSource(visit.getUtmSource())
                                .utmCampaign(visit.getUtmCampaign())
                                .acceptLanguage(visit.getAcceptLanguage())
                                .build())
                        .toList())
                .build();
    }

    private List<SiteInteractionMapPointResponse> buildMapPoints(List<SiteVisit> visits) {
        Map<String, List<SiteVisit>> grouped = visits.stream()
                .filter(visit -> isValidCoordinatePair(visit.getLatitude(), visit.getLongitude()))
                .collect(Collectors.groupingBy(this::mapClusterKey, LinkedHashMap::new, Collectors.toList()));

        return grouped.values().stream()
                .map(this::mapPoint)
                .sorted(Comparator
                        .comparingLong(SiteInteractionMapPointResponse::getVisits).reversed()
                        .thenComparing(SiteInteractionMapPointResponse::getLatestVisitAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAP_POINT_LIMIT)
                .toList();
    }

    private SiteInteractionMapPointResponse mapPoint(List<SiteVisit> visits) {
        double averageLatitude = visits.stream()
                .map(SiteVisit::getLatitude)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        double averageLongitude = visits.stream()
                .map(SiteVisit::getLongitude)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        SiteVisit latestVisit = visits.stream()
                .max(Comparator.comparing(SiteVisit::getCreatedAt))
                .orElse(visits.get(0));
        String dominantSourceType = visits.stream()
                .map(visit -> visit.getSourceType() != null ? visit.getSourceType().name() : SiteVisitSourceType.DIRECT.name())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(SiteVisitSourceType.DIRECT.name());
        String dominantSourceKey = visits.stream()
                .map(visit -> firstNonBlank(visit.getSourceLabel(), visit.getSourceType() != null ? visit.getSourceType().name() : null))
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse("Direct");

        return SiteInteractionMapPointResponse.builder()
                .latitude(roundCoordinate(averageLatitude))
                .longitude(roundCoordinate(averageLongitude))
                .locationName(firstNonBlank(
                        latestVisit.getExactLocationName(),
                        buildLocationName(latestVisit),
                        "Unknown location"
                ))
                .postalCode(latestVisit.getPostalCode())
                .countryName(latestVisit.getCountryName())
                .sourceType(dominantSourceType)
                .sourceLabel(dominantSourceKey)
                .visits(visits.size())
                .latestVisitAt(latestVisit.getCreatedAt())
                .build();
    }

    private String buildLocationName(SiteVisit visit) {
        return String.join(", ", java.util.stream.Stream.of(
                        visit.getCity(),
                        visit.getRegion(),
                        visit.getCountryName()
                )
                .filter(this::hasText)
                .toList());
    }

    private String mapClusterKey(SiteVisit visit) {
        if (hasText(visit.getPostalCode())) {
            return "PIN:" + visit.getPostalCode().trim().toUpperCase(Locale.ROOT);
        }
        if (hasText(visit.getExactLocationName())) {
            return "LOC:" + visit.getExactLocationName().trim().toLowerCase(Locale.ROOT);
        }
        return "COORD:" + roundToBuckets(visit.getLatitude()) + ":" + roundToBuckets(visit.getLongitude());
    }

    private double roundCoordinate(double value) {
        return Math.round(value * 100000d) / 100000d;
    }

    private double roundToBuckets(Double value) {
        if (value == null) {
            return 0;
        }
        return Math.round(value * 100d) / 100d;
    }

    private boolean enrichExistingVisit(SiteVisit visit,
                                        SiteVisitRequest request,
                                        String userAgent,
                                        String acceptLanguage,
                                        String clientIpAddress) {
        boolean changed = false;

        if (!hasText(visit.getAcceptLanguage()) && hasText(acceptLanguage)) {
            visit.setAcceptLanguage(truncate(normalize(acceptLanguage), 255));
            changed = true;
        }
        if (!hasText(visit.getUserAgent()) && hasText(userAgent)) {
            visit.setUserAgent(truncate(normalize(userAgent), 1000));
            changed = true;
        }

        GeoUpdateResult geoUpdateResult = applyGeoData(visit, request, clientIpAddress, true);
        return changed || geoUpdateResult.changed();
    }

    private SiteInteractionSummaryResponse buildSummary(boolean recorded) {
        return SiteInteractionSummaryResponse.builder()
                .totalVisits(siteVisitRepository.count())
                .recorded(recorded)
                .build();
    }

    private void enrichWithGeoData(SiteVisit visit, SiteVisitRequest request, String clientIpAddress) {
        applyGeoData(visit, request, clientIpAddress, false);
    }

    private GeoUpdateResult applyGeoData(SiteVisit visit,
                                         SiteVisitRequest request,
                                         String clientIpAddress,
                                         boolean fillOnlyWhenMissing) {
        boolean changed = false;
        boolean browserLocationApplied = false;

        String requestedTimezone = truncate(normalize(request != null ? request.getTimezone() : null), 255);
        Double requestedLatitude = request != null ? request.getLatitude() : null;
        Double requestedLongitude = request != null ? request.getLongitude() : null;
        Double requestedAccuracyMeters = request != null ? request.getAccuracyMeters() : null;

        if (isValidCoordinatePair(requestedLatitude, requestedLongitude)) {
            changed |= setField(visit.getLatitude(), requestedLatitude, fillOnlyWhenMissing, visit::setLatitude);
            changed |= setField(visit.getLongitude(), requestedLongitude, fillOnlyWhenMissing, visit::setLongitude);
            changed |= setField(visit.getLocationAccuracyMeters(), requestedAccuracyMeters, fillOnlyWhenMissing, visit::setLocationAccuracyMeters);
            changed |= setField(visit.getLocationSource(), "BROWSER", fillOnlyWhenMissing, visit::setLocationSource);
            if (hasText(requestedTimezone)) {
                changed |= setField(visit.getTimezone(), requestedTimezone, fillOnlyWhenMissing, visit::setTimezone);
            }

            var reverseLookup = geoLookupService.reverseLookup(requestedLatitude, requestedLongitude);
            changed |= applyLookupResult(visit, reverseLookup, fillOnlyWhenMissing, true);
            browserLocationApplied = true;
        } else if (hasText(requestedTimezone)) {
            changed |= setField(visit.getTimezone(), requestedTimezone, fillOnlyWhenMissing, visit::setTimezone);
        }

        String normalizedIp = resolvePublicIpAddress(clientIpAddress);
        if (hasText(normalizedIp)) {
            changed |= setField(visit.getIpAddress(), truncate(normalizedIp, 100), fillOnlyWhenMissing, visit::setIpAddress);
            var lookupResult = geoLookupService.lookup(normalizedIp);
            changed |= applyLookupResult(visit, lookupResult, fillOnlyWhenMissing || browserLocationApplied, false);
        }

        return new GeoUpdateResult(changed);
    }

    private boolean applyLookupResult(SiteVisit visit,
                                      com.retailshop.dto.GeoLookupResult lookupResult,
                                      boolean fillOnlyWhenMissing,
                                      boolean preciseLocation) {
        if (lookupResult == null) {
            return false;
        }

        boolean changed = false;
        changed |= setField(visit.getIpAddress(), truncate(lookupResult.getIpAddress(), 100), fillOnlyWhenMissing, visit::setIpAddress);
        changed |= setField(visit.getCity(), truncate(lookupResult.getCity(), 255), fillOnlyWhenMissing, visit::setCity);
        changed |= setField(visit.getRegion(), truncate(lookupResult.getRegion(), 255), fillOnlyWhenMissing, visit::setRegion);
        changed |= setField(visit.getCountryName(), truncate(lookupResult.getCountryName(), 255), fillOnlyWhenMissing, visit::setCountryName);
        changed |= setField(visit.getCountryCode(), truncate(lookupResult.getCountryCode(), 20), fillOnlyWhenMissing, visit::setCountryCode);
        changed |= setField(visit.getTimezone(), truncate(lookupResult.getTimezone(), 255), fillOnlyWhenMissing, visit::setTimezone);
        changed |= setField(visit.getOrganization(), truncate(lookupResult.getOrganization(), 255), fillOnlyWhenMissing, visit::setOrganization);
        changed |= setField(visit.getExactLocationName(), truncate(lookupResult.getLocationLabel(), 500), fillOnlyWhenMissing, visit::setExactLocationName);
        changed |= setField(visit.getPostalCode(), truncate(lookupResult.getPostalCode(), 40), fillOnlyWhenMissing, visit::setPostalCode);

        if (!fillOnlyWhenMissing || visit.getLatitude() == null) {
            changed |= setField(visit.getLatitude(), lookupResult.getLatitude(), fillOnlyWhenMissing, visit::setLatitude);
        }
        if (!fillOnlyWhenMissing || visit.getLongitude() == null) {
            changed |= setField(visit.getLongitude(), lookupResult.getLongitude(), fillOnlyWhenMissing, visit::setLongitude);
        }
        if (preciseLocation) {
            changed |= setField(visit.getLocationSource(), "BROWSER", fillOnlyWhenMissing, visit::setLocationSource);
        } else if (!hasText(visit.getLocationSource())) {
            changed |= setField(visit.getLocationSource(), "IP", false, visit::setLocationSource);
        }

        return changed;
    }

    private <T> boolean setField(T currentValue, T nextValue, boolean fillOnlyWhenMissing, java.util.function.Consumer<T> setter) {
        if (nextValue == null) {
            return false;
        }
        if (fillOnlyWhenMissing && currentValue != null && !(currentValue instanceof String stringValue && stringValue.isBlank())) {
            return false;
        }
        if (currentValue == null ? nextValue == null : currentValue.equals(nextValue)) {
            return false;
        }
        setter.accept(nextValue);
        return true;
    }

    private boolean isValidCoordinatePair(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private long totalForType(List<SiteInteractionSourceResponse> sourceBreakdown, SiteVisitSourceType sourceType) {
        return sourceBreakdown.stream()
                .filter(row -> sourceType.name().equals(row.getSourceType()))
                .mapToLong(SiteInteractionSourceResponse::getVisits)
                .sum();
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private String resolveLandingPath(String path) {
        String normalized = normalize(path);
        if (normalized == null) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return truncate(normalized, 500);
    }

    private SiteVisitSourceType resolveSourceType(String referrerHost, String utmSource, String utmMedium, String utmCampaign) {
        if (hasText(utmSource) || hasText(utmMedium) || hasText(utmCampaign)) {
            return SiteVisitSourceType.CAMPAIGN;
        }
        if (!hasText(referrerHost)) {
            return SiteVisitSourceType.DIRECT;
        }

        String host = referrerHost.toLowerCase(Locale.ROOT);
        if (SEARCH_HOSTS.stream().anyMatch(host::contains)) {
            return SiteVisitSourceType.SEARCH;
        }
        if (SOCIAL_HOSTS.stream().anyMatch(host::contains)) {
            return SiteVisitSourceType.SOCIAL;
        }
        return SiteVisitSourceType.REFERRAL;
    }

    private String resolveSourceLabel(SiteVisitSourceType sourceType,
                                      String referrerHost,
                                      String utmSource,
                                      String utmCampaign,
                                      String utmMedium) {
        return switch (sourceType) {
            case DIRECT -> "Direct";
            case CAMPAIGN -> firstNonBlank(utmSource, utmCampaign, utmMedium, "Campaign");
            case SEARCH, SOCIAL, REFERRAL -> firstNonBlank(referrerHost, sourceType.name());
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String extractHost(String referrer) {
        String normalized = normalize(referrer);
        if (normalized == null) {
            return null;
        }
        try {
            String host = URI.create(normalized).getHost();
            if (!hasText(host)) {
                return null;
            }
            return truncate(host.toLowerCase(Locale.ROOT), 255);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolvePublicIpAddress(String clientIpAddress) {
        String normalized = normalize(clientIpAddress);
        if (normalized == null) {
            return null;
        }

        String candidate = normalized.split(",")[0].trim();
        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                return null;
            }
            return candidate;
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record GeoUpdateResult(boolean changed) {
    }
}
