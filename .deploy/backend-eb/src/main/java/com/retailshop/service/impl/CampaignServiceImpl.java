package com.retailshop.service.impl;

import com.retailshop.dto.CampaignLogResponse;
import com.retailshop.dto.CampaignRequest;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.CampaignLog;
import com.retailshop.entity.Customer;
import com.retailshop.enums.CampaignStatus;
import com.retailshop.enums.CampaignType;
import com.retailshop.repository.CampaignLogRepository;
import com.retailshop.repository.CampaignRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.CampaignService;
import com.retailshop.service.SocialMediaService;
import com.retailshop.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final CustomerRepository customerRepository;
    private final WhatsAppService whatsAppService;
    private final SocialMediaService socialMediaService;

    @Override
    @Transactional
    public List<CampaignLogResponse> sendCampaign(CampaignRequest request) {
        Campaign campaign = new Campaign();
        campaign.setName(request.getName());
        campaign.setType(request.getType());
        campaign.setContent(request.getContent());
        Campaign savedCampaign = campaignRepository.save(campaign);

        List<CampaignLog> logs = new ArrayList<>();
        if (request.getType() == CampaignType.WHATSAPP) {
            for (Customer customer : customerRepository.findAll()) {
                CampaignLog log = new CampaignLog();
                log.setCampaign(savedCampaign);
                log.setCustomer(customer);
                log.setStatus(whatsAppService.sendMessage(customer, request.getContent())
                        ? CampaignStatus.MOCK_DELIVERED : CampaignStatus.FAILED);
                logs.add(log);
            }
        } else {
            boolean sent = request.getType() == CampaignType.INSTAGRAM
                    ? socialMediaService.postToInstagram(request.getContent())
                    : socialMediaService.postToFacebook(request.getContent());
            CampaignLog log = new CampaignLog();
            log.setCampaign(savedCampaign);
            log.setCustomer(null);
            log.setStatus(sent ? CampaignStatus.MOCK_DELIVERED : CampaignStatus.FAILED);
            logs.add(log);
        }

        return campaignLogRepository.saveAll(logs)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLogResponse> getHistory() {
        return campaignLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CampaignLogResponse mapToResponse(CampaignLog log) {
        return CampaignLogResponse.builder()
                .campaignId(log.getCampaign().getId())
                .campaignName(log.getCampaign().getName())
                .campaignType(log.getCampaign().getType())
                .customerId(log.getCustomer() != null ? log.getCustomer().getId() : null)
                .customerName(log.getCustomer() != null ? log.getCustomer().getName() : "Audience Broadcast")
                .customerMobile(log.getCustomer() != null ? log.getCustomer().getMobile() : "N/A")
                .status(log.getStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
