package com.retailshop.service.impl;

import com.retailshop.dto.CampaignLeadVisitRequest;
import com.retailshop.entity.CampaignLeadVisit;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CampaignLeadVisitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignLeadTrackingServiceImplTest {

    @Mock
    private CampaignLeadVisitRepository campaignLeadVisitRepository;

    @Test
    void shouldRecordCampaignLeadVisitWithNormalizedSource() {
        CampaignLeadTrackingServiceImpl service = new CampaignLeadTrackingServiceImpl(campaignLeadVisitRepository);
        UUID campaignId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2026, 5, 15, 9, 30);

        CampaignLeadVisitRequest request = new CampaignLeadVisitRequest();
        request.setCampaignId(campaignId);
        request.setSource(" Instagram ");
        request.setProductId(productId);
        request.setSessionId(" session-123 ");
        request.setTimestamp(timestamp);

        when(campaignLeadVisitRepository.save(any(CampaignLeadVisit.class))).thenAnswer(invocation -> {
            CampaignLeadVisit visit = invocation.getArgument(0);
            visit.prePersist();
            return visit;
        });

        var response = service.recordVisit(request);

        assertEquals(campaignId, response.getCampaignId());
        assertEquals("instagram", response.getSource());
        assertEquals(productId, response.getProductId());
        assertEquals("session-123", response.getSessionId());
        assertEquals(timestamp, response.getTimestamp());

        ArgumentCaptor<CampaignLeadVisit> captor = ArgumentCaptor.forClass(CampaignLeadVisit.class);
        verify(campaignLeadVisitRepository).save(captor.capture());
        assertEquals("instagram", captor.getValue().getSource());
    }

    @Test
    void shouldRejectUnsupportedSource() {
        CampaignLeadTrackingServiceImpl service = new CampaignLeadTrackingServiceImpl(campaignLeadVisitRepository);
        CampaignLeadVisitRequest request = new CampaignLeadVisitRequest();
        request.setCampaignId(UUID.randomUUID());
        request.setSource("twitter");
        request.setSessionId("session-123");

        assertThrows(BusinessException.class, () -> service.recordVisit(request));
        verifyNoInteractions(campaignLeadVisitRepository);
    }
}
