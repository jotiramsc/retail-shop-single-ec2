package com.retailshop.service.impl;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignLog;
import com.retailshop.enums.CampaignStatus;
import com.retailshop.enums.CampaignType;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CampaignLogRepository;
import com.retailshop.repository.CampaignRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.CampaignService;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.SocialMediaService;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final CustomerRepository customerRepository;
    private final WhatsAppMessageService whatsAppMessageService;
    private final SocialMediaService socialMediaService;

    @Override
    @Transactional
    public List<CampaignLogResponse> createCampaign(CampaignRequest request, String publishedBy) {
        List<CampaignType> channels = resolveChannels(request);
        Campaign campaign = new Campaign();
        campaign.setName(resolveTitle(request));
        campaign.setType(channels.get(0));
        campaign.setContent(request.getContent().trim());
        campaign.setOfferProduct(normalizeNullable(request.getOfferProduct()));
        campaign.setMediaUrl(normalizeNullable(request.getMediaUrl()));
        campaign.setHashtags(normalizeNullable(request.getHashtags()));
        campaign.setLinkUrl(normalizeNullable(request.getLinkUrl()));
        campaign.setChannels(channels.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse(""));
        campaign.setDraft(!request.isPublishNow());
        campaign.setCreatedBy(normalizeNullable(publishedBy));
        Campaign savedCampaign = campaignRepository.save(campaign);

        List<CampaignLog> logs = new ArrayList<>();
        for (CampaignType channel : channels) {
            CampaignLog log = new CampaignLog();
            log.setCampaign(savedCampaign);
            log.setCustomer(null);
            log.setChannel(channel);
            log.setContent(savedCampaign.getContent());
            log.setMediaUrl(savedCampaign.getMediaUrl());
            log.setStatus(request.isPublishNow() ? CampaignStatus.PENDING : CampaignStatus.DRAFT);
            logs.add(log);
        }
        List<CampaignLog> savedLogs = campaignLogRepository.saveAll(logs);
        if (!request.isPublishNow()) {
            return savedLogs.stream().map(this::mapToResponse).toList();
        }
        return publishLogs(savedCampaign, savedLogs, publishedBy);
    }

    @Override
    @Transactional
    public List<CampaignLogResponse> publishCampaign(UUID campaignId, String publishedBy) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        campaign.setDraft(false);
        campaignRepository.save(campaign);

        List<CampaignType> channels = parseChannels(campaign.getChannels(), campaign.getType());
        Map<CampaignType, CampaignLog> latestByChannel = new EnumMap<>(CampaignType.class);
        for (CampaignLog log : campaignLogRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)) {
            if (log.getChannel() != null && !latestByChannel.containsKey(log.getChannel())) {
                latestByChannel.put(log.getChannel(), log);
            }
        }

        List<CampaignLog> logs = new ArrayList<>();
        for (CampaignType channel : channels) {
            CampaignLog log = latestByChannel.getOrDefault(channel, buildLog(campaign, channel));
            log.setCampaign(campaign);
            log.setChannel(channel);
            log.setContent(campaign.getContent());
            log.setMediaUrl(campaign.getMediaUrl());
            log.setStatus(CampaignStatus.PENDING);
            logs.add(campaignLogRepository.save(log));
        }
        return publishLogs(campaign, logs, publishedBy);
    }

    @Override
    @Transactional
    public CampaignLogResponse retryCampaignLog(UUID campaignLogId, String publishedBy) {
        CampaignLog log = campaignLogRepository.findById(campaignLogId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign publish record not found"));
        Campaign campaign = log.getCampaign();
        if (campaign == null) {
            throw new ResourceNotFoundException("Campaign not found");
        }
        log.setStatus(CampaignStatus.PENDING);
        log.setErrorMessage(null);
        log.setPlatformResponseId(null);
        log.setPublishedAt(null);
        log.setPublishedBy(null);
        CampaignLog updated = publishSingleLog(campaignLogRepository.save(log), campaign, publishedBy);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<CampaignLogResponse> getHistory(Pageable pageable) {
        return PaginatedResponse.from(campaignLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse));
    }

    private List<CampaignLogResponse> publishLogs(Campaign campaign, List<CampaignLog> logs, String publishedBy) {
        List<CampaignLog> publishedLogs = new ArrayList<>();
        for (CampaignLog log : logs) {
            publishedLogs.add(publishSingleLog(log, campaign, publishedBy));
        }
        return publishedLogs.stream().map(this::mapToResponse).toList();
    }

    private CampaignLog publishSingleLog(CampaignLog log, Campaign campaign, String publishedBy) {
        MarketingChannelResult result = switch (log.getChannel()) {
            case WHATSAPP -> whatsAppMessageService.publishCampaign(campaign, customerRepository.findAll());
            case INSTAGRAM -> socialMediaService.publishInstagram(campaign);
            case FACEBOOK -> socialMediaService.publishFacebook(campaign);
        };

        if (result.isSuccess()) {
            log.setStatus(CampaignStatus.PUBLISHED);
            log.setPlatformResponseId(normalizeNullable(result.getResponseId()));
            log.setErrorMessage(null);
            log.setPublishedBy(normalizeNullable(publishedBy));
            log.setPublishedAt(LocalDateTime.now());
        } else {
            log.setStatus(CampaignStatus.FAILED);
            log.setPlatformResponseId(normalizeNullable(result.getResponseId()));
            log.setErrorMessage(normalizeNullable(result.getErrorMessage()));
            log.setPublishedBy(normalizeNullable(publishedBy));
            log.setPublishedAt(null);
        }
        return campaignLogRepository.save(log);
    }

    private CampaignLog buildLog(Campaign campaign, CampaignType channel) {
        CampaignLog log = new CampaignLog();
        log.setCampaign(campaign);
        log.setCustomer(null);
        log.setChannel(channel);
        log.setContent(campaign.getContent());
        log.setMediaUrl(campaign.getMediaUrl());
        log.setStatus(CampaignStatus.DRAFT);
        return log;
    }

    private List<CampaignType> resolveChannels(CampaignRequest request) {
        if (request.getChannels() != null && !request.getChannels().isEmpty()) {
            return request.getChannels().stream().distinct().toList();
        }
        if (request.getType() != null) {
            return List.of(request.getType());
        }
        throw new BusinessException("At least one marketing channel must be selected");
    }

    private List<CampaignType> parseChannels(String rawChannels, CampaignType fallbackType) {
        if (rawChannels != null && !rawChannels.isBlank()) {
            return List.of(rawChannels.split(",")).stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> CampaignType.valueOf(value.toUpperCase(Locale.ROOT)))
                    .distinct()
                    .toList();
        }
        if (fallbackType != null) {
            return List.of(fallbackType);
        }
        throw new BusinessException("Campaign has no channels configured");
    }

    private String resolveTitle(CampaignRequest request) {
        String title = normalizeNullable(request.getTitle());
        if (title != null) {
            return title;
        }
        title = normalizeNullable(request.getName());
        if (title != null) {
            return title;
        }
        throw new BusinessException("Campaign title is required");
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CampaignLogResponse mapToResponse(CampaignLog log) {
        return CampaignLogResponse.builder()
                .id(log.getId())
                .campaignId(log.getCampaign().getId())
                .campaignName(log.getCampaign().getName())
                .channel(log.getChannel())
                .status(log.getStatus())
                .content(log.getContent())
                .mediaUrl(log.getMediaUrl())
                .platformResponseId(log.getPlatformResponseId())
                .errorMessage(log.getErrorMessage())
                .publishedBy(log.getPublishedBy())
                .publishedAt(log.getPublishedAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
