package com.retailshop.service.impl;

import com.retailshop.dto.CampaignLeadVisitRequest;
import com.retailshop.dto.CampaignLeadVisitResponse;
import com.retailshop.entity.CampaignLeadVisit;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CampaignLeadVisitRepository;
import com.retailshop.service.CampaignLeadTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CampaignLeadTrackingServiceImpl implements CampaignLeadTrackingService {

    private static final Set<String> ACCEPTED_SOURCES = Set.of("facebook", "instagram", "whatsapp", "direct");

    private final CampaignLeadVisitRepository campaignLeadVisitRepository;

    @Override
    @Transactional
    public CampaignLeadVisitResponse recordVisit(CampaignLeadVisitRequest request) {
        String source = normalizeSource(request.getSource());

        CampaignLeadVisit visit = new CampaignLeadVisit();
        visit.setCampaignId(request.getCampaignId());
        visit.setSource(source);
        visit.setProductId(request.getProductId());
        visit.setOfferId(request.getOfferId());
        visit.setSessionId(request.getSessionId().trim());
        visit.setVisitedAt(request.getTimestamp());

        return map(campaignLeadVisitRepository.save(visit));
    }

    private String normalizeSource(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (!ACCEPTED_SOURCES.contains(normalized)) {
            throw new BusinessException("Invalid campaign lead source. Accepted sources: facebook, instagram, whatsapp, direct");
        }
        return normalized;
    }

    private CampaignLeadVisitResponse map(CampaignLeadVisit visit) {
        return CampaignLeadVisitResponse.builder()
                .id(visit.getId())
                .campaignId(visit.getCampaignId())
                .source(visit.getSource())
                .productId(visit.getProductId())
                .offerId(visit.getOfferId())
                .sessionId(visit.getSessionId())
                .timestamp(visit.getVisitedAt())
                .createdAt(visit.getCreatedAt())
                .build();
    }
}
