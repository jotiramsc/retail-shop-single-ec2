package com.retailshop.service.impl;

import com.retailshop.dto.ApprovalHistoryResponse;
import com.retailshop.dto.MarketingAnalyticsResponse;
import com.retailshop.dto.MarketingApprovalQueueItemResponse;
import com.retailshop.dto.MarketingApprovalRequest;
import com.retailshop.dto.MarketingCampaignListItemResponse;
import com.retailshop.dto.MarketingCampaignRequest;
import com.retailshop.dto.MarketingCampaignResponse;
import com.retailshop.dto.MarketingCampaignSuggestionResponse;
import com.retailshop.dto.MarketingContentResponse;
import com.retailshop.dto.MarketingContentUpdateRequest;
import com.retailshop.dto.MarketingRejectRequest;
import com.retailshop.dto.MarketingScheduleRequest;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.ApprovalHistory;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignAnalytics;
import com.retailshop.entity.CampaignContent;
import com.retailshop.entity.Product;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.entity.PublishLog;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.CampaignType;
import com.retailshop.enums.MarketingApprovalAction;
import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingContentStatus;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;
import com.retailshop.enums.MarketingWorkflowStatus;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.ApprovalHistoryRepository;
import com.retailshop.repository.CampaignAnalyticsRepository;
import com.retailshop.repository.CampaignContentRepository;
import com.retailshop.repository.CampaignLogRepository;
import com.retailshop.repository.CampaignRepository;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.PublishLogRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.AIContentGenerationService;
import com.retailshop.service.MarketingAutomationService;
import com.retailshop.service.SocialPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAutomationServiceImpl implements MarketingAutomationService {

    private final CampaignRepository campaignRepository;
    private final CampaignContentRepository campaignContentRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final PublishLogRepository publishLogRepository;
    private final CampaignAnalyticsRepository campaignAnalyticsRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryOptionRepository productCategoryRepository;
    private final ReceiptSettingsRepository receiptSettingsRepository;
    private final AIContentGenerationService aiContentGenerationService;
    private final List<SocialPublisher> publishers;

    @Override
    @Transactional
    public MarketingCampaignResponse createCampaign(MarketingCampaignRequest request, String actor) {
        Campaign campaign = new Campaign();
        applyCampaignRequest(campaign, request, actor);
        campaign.setStatus(MarketingWorkflowStatus.DRAFT);
        campaign.setDraft(true);
        Campaign saved = campaignRepository.save(campaign);
        return mapCampaignResponse(saved, List.of());
    }

    @Override
    @Transactional
    public MarketingCampaignResponse startCampaignGeneration(UUID campaignId) {
        Campaign campaign = getCampaignEntity(campaignId);
        campaign.setStatus(MarketingWorkflowStatus.GENERATING);
        campaign.setDraft(false);
        Campaign saved = campaignRepository.save(campaign);
        List<CampaignContent> contents = campaignContentRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        return mapCampaignResponse(saved, contents);
    }

    @Override
    @Transactional
    public MarketingCampaignResponse generateCampaign(UUID campaignId, String actor) {
        Campaign campaign = getCampaignEntity(campaignId);
        String shopName = resolveShopName();
        String categoryName = resolveCategoryName(campaign.getCategoryId());
        String productName = resolveProductName(campaign.getProductId());
        Map<MarketingPlatform, CampaignContent> existingByPlatform = campaignContentRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId)
                .stream()
                .collect(Collectors.toMap(CampaignContent::getPlatform, Function.identity(), (left, right) -> right, LinkedHashMap::new));
        List<MarketingPlatform> targetPlatforms = parsePlatforms(campaign.getTargetPlatforms());
        String visualSeed = UUID.randomUUID().toString();
        AIContentGenerationService.GeneratedCreativeImage sharedCreative =
                aiContentGenerationService.generateSharedCreativeImage(campaign, shopName, categoryName, productName, visualSeed);

        List<CampaignContent> updatedContents = new ArrayList<>();
        for (MarketingPlatform platform : targetPlatforms) {
            AIContentGenerationService.GeneratedMarketingDraft draft =
                    aiContentGenerationService.generateDraft(campaign, shopName, categoryName, productName, platform);
            CampaignContent content = existingByPlatform.getOrDefault(platform, new CampaignContent());
            content.setCampaign(campaign);
            content.setPlatform(platform);
            content.setCaptionText(draft.captionText());
            content.setHashtags(draft.hashtags());
            content.setCallToAction(draft.callToAction());
            content.setImagePrompt(defaultString(sharedCreative.imagePrompt(), draft.imagePrompt()));
            content.setImageUrl(defaultString(sharedCreative.imageUrl(), draft.imageUrl()));
            content.setStatus(MarketingContentStatus.PENDING_APPROVAL);
            content.setRejectionReason(null);
            content.setScheduledAt(null);
            updatedContents.add(campaignContentRepository.save(content));
        }

        campaign.setDraft(false);
        campaign.setStatus(MarketingWorkflowStatus.PENDING_APPROVAL);
        campaign.setContent(updatedContents.stream()
                .map(CampaignContent::getCaptionText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(""));
        campaignRepository.save(campaign);
        return mapCampaignResponse(campaign, updatedContents);
    }

    @Override
    @Transactional
    public void markCampaignGenerationFailed(UUID campaignId) {
        campaignRepository.findById(campaignId).ifPresent(campaign -> {
            campaign.setDraft(false);
            campaign.setStatus(MarketingWorkflowStatus.FAILED);
            campaignRepository.save(campaign);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<MarketingCampaignListItemResponse> getCampaigns(MarketingWorkflowStatus status,
                                                                            MarketingPlatform platform,
                                                                            MarketingCampaignType campaignType,
                                                                            LocalDate fromDate,
                                                                            LocalDate toDate,
                                                                            Pageable pageable) {
        Specification<Campaign> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (campaignType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("campaignType"), campaignType));
        }
        if (platform != null) {
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("targetPlatforms")), "%" + platform.name() + "%"));
        }
        if (fromDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate.atTime(LocalTime.MAX)));
        }

        Page<Campaign> page = campaignRepository.findAll(spec, pageable);
        Map<UUID, List<CampaignContent>> contentsByCampaign = campaignContentRepository.findAll().stream()
                .collect(Collectors.groupingBy(content -> content.getCampaign().getId()));
        return PaginatedResponse.from(page.map(campaign -> mapCampaignListItem(campaign, contentsByCampaign.getOrDefault(campaign.getId(), List.of()))));
    }

    @Override
    @Transactional(readOnly = true)
    public MarketingCampaignResponse getCampaign(UUID campaignId) {
        Campaign campaign = getCampaignEntity(campaignId);
        List<CampaignContent> contents = campaignContentRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        return mapCampaignResponse(campaign, contents);
    }

    @Override
    @Transactional
    public void deleteCampaign(UUID campaignId) {
        Campaign campaign = getCampaignEntity(campaignId);
        campaignLogRepository.deleteByCampaignId(campaignId);
        campaignRepository.delete(campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketingCampaignSuggestionResponse> getCampaignSuggestions(int daysAhead) {
        return MarketingOccasionLibrary.buildSuggestions(LocalDate.now(), Math.max(daysAhead, 1), "https://kpskrishnai.com");
    }

    private String resolveShopName() {
        return receiptSettingsRepository.findAll().stream()
                .findFirst()
                .map(ReceiptSettings::getShopName)
                .filter(value -> value != null && !value.isBlank())
                .orElse("Krishnai Pearl Shopee");
    }

    @Override
    @Transactional
    public MarketingContentResponse updateContent(UUID contentId, MarketingContentUpdateRequest request, String actor, boolean owner) {
        CampaignContent content = getContentEntity(contentId);
        if (content.getStatus() == MarketingContentStatus.PUBLISHED) {
            throw new BusinessException("Published content cannot be edited");
        }
        content.setCaptionText(trimToNull(request.getCaptionText()));
        content.setHashtags(trimToNull(request.getHashtags()));
        content.setCallToAction(trimToNull(request.getCallToAction()));
        content.setImagePrompt(trimToNull(request.getImagePrompt()));
        if (request.getImageUrl() != null) {
            content.setImageUrl(trimToNull(request.getImageUrl()));
        }
        if (request.getScheduledAt() != null) {
            content.setScheduledAt(request.getScheduledAt());
        }
        content.setRejectionReason(null);

        boolean approvedOrScheduled = content.getStatus() == MarketingContentStatus.APPROVED
                || content.getStatus() == MarketingContentStatus.SCHEDULED;
        if (approvedOrScheduled && !owner) {
            content.setStatus(MarketingContentStatus.PENDING_APPROVAL);
            content.setScheduledAt(null);
            updateCampaignStatus(content.getCampaign());
        } else if (approvedOrScheduled) {
            content.setStatus(content.getScheduledAt() != null && content.getScheduledAt().isAfter(LocalDateTime.now())
                    ? MarketingContentStatus.SCHEDULED
                    : MarketingContentStatus.APPROVED);
            updateCampaignStatus(content.getCampaign());
        } else {
            content.setStatus(MarketingContentStatus.PENDING_APPROVAL);
            updateCampaignStatus(content.getCampaign());
        }

        CampaignContent saved = campaignContentRepository.save(content);
        addApprovalHistory(saved, MarketingApprovalAction.EDITED, "Edited by " + actor, actor);
        return mapContentResponse(saved);
    }

    @Override
    @Transactional
    public MarketingContentResponse approveContent(UUID contentId, MarketingApprovalRequest request, String actor) {
        CampaignContent content = getContentEntity(contentId);
        if (content.getCaptionText() == null || content.getCaptionText().isBlank()) {
            throw new BusinessException("Caption cannot be empty before approval");
        }
        content.setStatus(MarketingContentStatus.APPROVED);
        content.setRejectionReason(null);
        CampaignContent saved = campaignContentRepository.save(content);
        addApprovalHistory(saved, MarketingApprovalAction.APPROVED, trimToNull(request.getComment()), actor);
        updateCampaignStatus(saved.getCampaign());
        return mapContentResponse(saved);
    }

    @Override
    @Transactional
    public MarketingContentResponse rejectContent(UUID contentId, MarketingRejectRequest request, String actor) {
        CampaignContent content = getContentEntity(contentId);
        content.setStatus(MarketingContentStatus.REJECTED);
        content.setRejectionReason(request.getReason().trim());
        content.setScheduledAt(null);
        CampaignContent saved = campaignContentRepository.save(content);
        addApprovalHistory(saved, MarketingApprovalAction.REJECTED, request.getReason().trim(), actor);
        updateCampaignStatus(saved.getCampaign());
        return mapContentResponse(saved);
    }

    @Override
    @Transactional
    public MarketingContentResponse scheduleContent(UUID contentId, MarketingScheduleRequest request, String actor) {
        CampaignContent content = getContentEntity(contentId);
        if (content.getStatus() == MarketingContentStatus.REJECTED || content.getStatus() == MarketingContentStatus.GENERATED) {
            throw new BusinessException("Rejected or unapproved content cannot be scheduled");
        }
        if (content.getStatus() != MarketingContentStatus.APPROVED) {
            throw new BusinessException("Only approved content can be scheduled");
        }
        if (!request.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Scheduled time must be in the future");
        }
        content.setScheduledAt(request.getScheduledAt());
        content.setStatus(MarketingContentStatus.SCHEDULED);
        CampaignContent saved = campaignContentRepository.save(content);
        updateCampaignStatus(saved.getCampaign());
        return mapContentResponse(saved);
    }

    @Override
    @Transactional
    public MarketingContentResponse publishNow(UUID contentId, String actor) {
        CampaignContent content = getContentEntity(contentId);
        if (content.getStatus() != MarketingContentStatus.APPROVED && content.getStatus() != MarketingContentStatus.SCHEDULED) {
            throw new BusinessException("Only approved content can be published");
        }
        CampaignContent saved = publishContent(content, actor);
        return mapContentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketingApprovalQueueItemResponse> getApprovalQueue() {
        return campaignContentRepository.findByStatusOrderByCreatedAtAsc(MarketingContentStatus.PENDING_APPROVAL)
                .stream()
                .map(content -> MarketingApprovalQueueItemResponse.builder()
                        .contentId(content.getId())
                        .campaignId(content.getCampaign().getId())
                        .campaignName(content.getCampaign().getCampaignName())
                        .platform(content.getPlatform())
                        .captionText(content.getCaptionText())
                        .hashtags(content.getHashtags())
                        .callToAction(content.getCallToAction())
                        .imagePrompt(content.getImagePrompt())
                        .imageUrl(content.getImageUrl())
                        .createdBy(content.getCampaign().getCreatedBy())
                        .createdAt(content.getCreatedAt())
                        .updatedAt(content.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MarketingAnalyticsResponse getAnalytics(UUID campaignId, MarketingPlatform platform, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime start = (fromDate == null ? LocalDate.now().minusDays(30) : fromDate).atStartOfDay();
        LocalDateTime end = (toDate == null ? LocalDate.now() : toDate).atTime(LocalTime.MAX);
        List<CampaignAnalytics> rows = campaignAnalyticsRepository.findForReport(campaignId, platform, start, end);

        long impressions = rows.stream().mapToLong(CampaignAnalytics::getImpressions).sum();
        long likes = rows.stream().mapToLong(CampaignAnalytics::getLikes).sum();
        long comments = rows.stream().mapToLong(CampaignAnalytics::getComments).sum();
        long shares = rows.stream().mapToLong(CampaignAnalytics::getShares).sum();
        long clicks = rows.stream().mapToLong(CampaignAnalytics::getClicks).sum();
        long conversions = rows.stream().mapToLong(CampaignAnalytics::getConversions).sum();

        Map<MarketingPlatform, List<CampaignAnalytics>> byPlatform = rows.stream()
                .collect(Collectors.groupingBy(CampaignAnalytics::getPlatform, () -> new EnumMap<>(MarketingPlatform.class), Collectors.toList()));

        List<MarketingAnalyticsResponse.PlatformAnalyticsRow> platformRows = byPlatform.entrySet().stream()
                .map(entry -> MarketingAnalyticsResponse.PlatformAnalyticsRow.builder()
                        .platform(entry.getKey())
                        .impressions(entry.getValue().stream().mapToLong(CampaignAnalytics::getImpressions).sum())
                        .likes(entry.getValue().stream().mapToLong(CampaignAnalytics::getLikes).sum())
                        .comments(entry.getValue().stream().mapToLong(CampaignAnalytics::getComments).sum())
                        .shares(entry.getValue().stream().mapToLong(CampaignAnalytics::getShares).sum())
                        .clicks(entry.getValue().stream().mapToLong(CampaignAnalytics::getClicks).sum())
                        .conversions(entry.getValue().stream().mapToLong(CampaignAnalytics::getConversions).sum())
                        .build())
                .sorted(Comparator.comparing(row -> row.getPlatform().name()))
                .toList();

        List<MarketingAnalyticsResponse.CampaignAnalyticsRow> campaignRows = rows.stream()
                .map(row -> MarketingAnalyticsResponse.CampaignAnalyticsRow.builder()
                        .campaignId(row.getCampaignContent().getCampaign().getId())
                        .contentId(row.getCampaignContent().getId())
                        .campaignName(row.getCampaignContent().getCampaign().getCampaignName())
                        .platform(row.getPlatform())
                        .impressions(row.getImpressions())
                        .likes(row.getLikes())
                        .comments(row.getComments())
                        .shares(row.getShares())
                        .clicks(row.getClicks())
                        .conversions(row.getConversions())
                        .fetchedAt(row.getFetchedAt())
                        .build())
                .toList();

        return MarketingAnalyticsResponse.builder()
                .impressions(impressions)
                .likes(likes)
                .comments(comments)
                .shares(shares)
                .clicks(clicks)
                .conversions(conversions)
                .byPlatform(platformRows)
                .byCampaign(campaignRows)
                .build();
    }

    @Override
    @Transactional
    public void publishScheduled() {
        List<CampaignContent> dueContents = campaignContentRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                MarketingContentStatus.SCHEDULED,
                LocalDateTime.now()
        );
        for (CampaignContent content : dueContents) {
            publishContent(content, "scheduler");
        }
    }

    private CampaignContent publishContent(CampaignContent content, String actor) {
        SocialPublisher publisher = publishers.stream()
                .filter(candidate -> candidate.platform() == content.getPlatform())
                .findFirst()
                .orElseThrow(() -> new BusinessException("No publisher configured for " + content.getPlatform()));

        SocialPublisher.PublishResult result = publisher.publish(content);
        PublishLog logEntry = new PublishLog();
        logEntry.setCampaignContent(content);
        logEntry.setPlatform(content.getPlatform());
        logEntry.setRequestPayload(result.requestPayload());
        logEntry.setResponsePayload(result.responsePayload());
        logEntry.setStatus(result.success() ? "PUBLISHED" : "FAILED");
        logEntry.setErrorMessage(trimToNull(result.errorMessage()));
        publishLogRepository.save(logEntry);

        if (result.success()) {
            content.setStatus(MarketingContentStatus.PUBLISHED);
            content.setExternalPostId(trimToNull(result.externalPostId()));
            content.setPublishedAt(LocalDateTime.now());
            campaignContentRepository.save(content);

            CampaignAnalytics analytics = new CampaignAnalytics();
            analytics.setCampaignContent(content);
            analytics.setPlatform(content.getPlatform());
            analytics.setImpressions(0);
            analytics.setLikes(0);
            analytics.setComments(0);
            analytics.setShares(0);
            analytics.setClicks(0);
            analytics.setConversions(0);
            campaignAnalyticsRepository.save(analytics);
        } else {
            content.setStatus(MarketingContentStatus.FAILED);
            content.setRejectionReason(trimToNull(result.errorMessage()));
            campaignContentRepository.save(content);
        }
        updateCampaignStatus(content.getCampaign());
        log.info("Marketing content {} publish result by {} -> {}", content.getId(), actor, content.getStatus());
        return content;
    }

    private Campaign getCampaignEntity(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
    }

    private CampaignContent getContentEntity(UUID contentId) {
        return campaignContentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign content not found"));
    }

    private void applyCampaignRequest(Campaign campaign, MarketingCampaignRequest request, String actor) {
        campaign.setCampaignName(request.getCampaignName().trim());
        campaign.setName(request.getCampaignName().trim());
        campaign.setCampaignType(request.getCampaignType());
        campaign.setCategoryId(request.getCategoryId());
        campaign.setProductId(request.getProductId());
        campaign.setOfferTitle(trimToNull(request.getOfferTitle()));
        campaign.setLinkUrl(trimToNull(request.getLandingUrl()));
        campaign.setDiscountType(request.getDiscountType() == null ? MarketingDiscountType.NONE : request.getDiscountType());
        campaign.setDiscountValue(normalizeDiscountValue(request.getDiscountValue()));
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setTargetPlatforms(joinPlatforms(request.getTargetPlatforms()));
        campaign.setChannels(joinPlatforms(request.getTargetPlatforms()));
        campaign.setType(toLegacyCampaignType(request.getTargetPlatforms()));
        campaign.setLanguage(request.getLanguage() == null ? MarketingLanguage.ENGLISH : request.getLanguage());
        campaign.setTone(request.getTone() == null ? MarketingTone.PREMIUM : request.getTone());
        campaign.setCreatedBy(actor);
        campaign.setContent("");
    }

    private BigDecimal normalizeDiscountValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private CampaignType toLegacyCampaignType(Set<MarketingPlatform> platforms) {
        return parsePlatforms(joinPlatforms(platforms)).stream()
                .findFirst()
                .map(platform -> switch (platform) {
                    case INSTAGRAM -> CampaignType.INSTAGRAM;
                    case FACEBOOK -> CampaignType.FACEBOOK;
                    case WHATSAPP -> CampaignType.WHATSAPP;
                })
                .orElse(CampaignType.WHATSAPP);
    }

    private String joinPlatforms(Set<MarketingPlatform> platforms) {
        return platforms == null ? "" : platforms.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<MarketingPlatform> parsePlatforms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> MarketingPlatform.valueOf(value.toUpperCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private MarketingCampaignListItemResponse mapCampaignListItem(Campaign campaign, List<CampaignContent> contents) {
        Map<MarketingContentStatus, Long> counts = contents.stream()
                .collect(Collectors.groupingBy(CampaignContent::getStatus, Collectors.counting()));
        LocalDateTime nextScheduled = contents.stream()
                .map(CampaignContent::getScheduledAt)
                .filter(value -> value != null && value.isAfter(LocalDateTime.now().minusYears(1)))
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return MarketingCampaignListItemResponse.builder()
                .id(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .campaignType(campaign.getCampaignType())
                .targetPlatforms(parsePlatforms(campaign.getTargetPlatforms()))
                .status(campaign.getStatus())
                .createdBy(campaign.getCreatedBy())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .contentCount(contents.size())
                .pendingApprovalCount(counts.getOrDefault(MarketingContentStatus.PENDING_APPROVAL, 0L))
                .approvedCount(counts.getOrDefault(MarketingContentStatus.APPROVED, 0L))
                .scheduledCount(counts.getOrDefault(MarketingContentStatus.SCHEDULED, 0L))
                .publishedCount(counts.getOrDefault(MarketingContentStatus.PUBLISHED, 0L))
                .failedCount(counts.getOrDefault(MarketingContentStatus.FAILED, 0L))
                .nextScheduledAt(nextScheduled)
                .build();
    }

    private MarketingCampaignResponse mapCampaignResponse(Campaign campaign, List<CampaignContent> contents) {
        String categoryName = resolveCategoryName(campaign.getCategoryId());
        String productName = resolveProductName(campaign.getProductId());
        return MarketingCampaignResponse.builder()
                .id(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .campaignType(campaign.getCampaignType())
                .categoryId(campaign.getCategoryId())
                .productId(campaign.getProductId())
                .categoryName(categoryName)
                .productName(productName)
                .offerTitle(campaign.getOfferTitle())
                .landingUrl(campaign.getLinkUrl())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .targetPlatforms(parsePlatforms(campaign.getTargetPlatforms()))
                .language(campaign.getLanguage())
                .tone(campaign.getTone())
                .status(campaign.getStatus())
                .createdBy(campaign.getCreatedBy())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .contents(contents.stream().map(this::mapContentResponse).toList())
                .build();
    }

    private MarketingContentResponse mapContentResponse(CampaignContent content) {
        List<ApprovalHistoryResponse> history = approvalHistoryRepository.findByCampaignContentIdOrderByActionAtDesc(content.getId())
                .stream()
                .map(entry -> ApprovalHistoryResponse.builder()
                        .id(entry.getId())
                        .action(entry.getAction())
                        .comment(entry.getComment())
                        .actionBy(entry.getActionBy())
                        .actionAt(entry.getActionAt())
                        .build())
                .toList();

        return MarketingContentResponse.builder()
                .id(content.getId())
                .campaignId(content.getCampaign().getId())
                .platform(content.getPlatform())
                .captionText(content.getCaptionText())
                .hashtags(content.getHashtags())
                .callToAction(content.getCallToAction())
                .imagePrompt(content.getImagePrompt())
                .imageUrl(content.getImageUrl())
                .status(content.getStatus())
                .rejectionReason(content.getRejectionReason())
                .scheduledAt(content.getScheduledAt())
                .publishedAt(content.getPublishedAt())
                .externalPostId(content.getExternalPostId())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .approvalHistory(history)
                .build();
    }

    private void addApprovalHistory(CampaignContent content, MarketingApprovalAction action, String comment, String actor) {
        ApprovalHistory history = new ApprovalHistory();
        history.setCampaignContent(content);
        history.setAction(action);
        history.setComment(trimToNull(comment));
        history.setActionBy(actor);
        approvalHistoryRepository.save(history);
    }

    private void updateCampaignStatus(Campaign campaign) {
        List<CampaignContent> contents = campaignContentRepository.findByCampaignIdOrderByCreatedAtAsc(campaign.getId());
        if (contents.isEmpty()) {
            campaign.setStatus(MarketingWorkflowStatus.DRAFT);
            campaign.setDraft(true);
            campaignRepository.save(campaign);
            return;
        }

        Set<MarketingContentStatus> statuses = contents.stream()
                .map(CampaignContent::getStatus)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        MarketingWorkflowStatus nextStatus;
        if (statuses.stream().allMatch(status -> status == MarketingContentStatus.PUBLISHED)) {
            nextStatus = MarketingWorkflowStatus.PUBLISHED;
        } else if (statuses.contains(MarketingContentStatus.FAILED)) {
            nextStatus = MarketingWorkflowStatus.FAILED;
        } else if (statuses.contains(MarketingContentStatus.SCHEDULED)) {
            nextStatus = MarketingWorkflowStatus.SCHEDULED;
        } else if (statuses.contains(MarketingContentStatus.REJECTED) && statuses.size() == 1) {
            nextStatus = MarketingWorkflowStatus.REJECTED;
        } else if (statuses.stream().allMatch(status -> status == MarketingContentStatus.APPROVED)) {
            nextStatus = MarketingWorkflowStatus.APPROVED;
        } else if (statuses.contains(MarketingContentStatus.PENDING_APPROVAL) || statuses.contains(MarketingContentStatus.EDITED)) {
            nextStatus = MarketingWorkflowStatus.PENDING_APPROVAL;
        } else {
            nextStatus = MarketingWorkflowStatus.GENERATED;
        }

        campaign.setStatus(nextStatus);
        campaign.setDraft(nextStatus == MarketingWorkflowStatus.DRAFT);
        campaignRepository.save(campaign);
    }

    private String resolveCategoryName(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return productCategoryRepository.findById(categoryId)
                .map(ProductCategoryOption::getDisplayName)
                .orElse(null);
    }

    private String resolveProductName(UUID productId) {
        if (productId == null) {
            return null;
        }
        return productRepository.findById(productId)
                .map(Product::getName)
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
