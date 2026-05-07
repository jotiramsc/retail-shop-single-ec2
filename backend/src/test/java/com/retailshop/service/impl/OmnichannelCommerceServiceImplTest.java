package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.OmnichannelProperties;
import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.repository.AiRecommendationLogRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.SocialWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmnichannelCommerceServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OmnichannelLeadRepository leadRepository;

    @Mock
    private OmnichannelConversationRepository conversationRepository;

    @Mock
    private OmnichannelConversationMessageRepository messageRepository;

    @Mock
    private SocialWebhookEventRepository webhookEventRepository;

    @Mock
    private AiRecommendationLogRepository recommendationLogRepository;

    private OmnichannelCommerceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OmnichannelCommerceServiceImpl(
                new OmnichannelProperties(),
                productRepository,
                leadRepository,
                conversationRepository,
                messageRepository,
                webhookEventRepository,
                recommendationLogRepository,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCaptureLeadFromN8nAliasFields() {
        OmnichannelLeadRequest request = new OmnichannelLeadRequest();
        request.setChannel("whatsapp");
        request.setExternalId("919175834000");
        request.setSourceMessageId("wamid.demo");
        request.setCustomerName("Aarti");
        request.setCampaignName("Wedding Lead Campaign");
        request.setQuery("Show pearl earrings under 2000");
        request.setBudget("2000");
        request.setOccasion("wedding");
        request.setLanguage("hinglish");

        when(leadRepository.findFirstByChannelAndExternalUserIdOrderByUpdatedAtDesc("WHATSAPP", "919175834000"))
                .thenReturn(Optional.empty());
        when(leadRepository.save(any(OmnichannelLead.class))).thenAnswer(invocation -> {
            OmnichannelLead lead = invocation.getArgument(0);
            lead.prePersist();
            return lead;
        });
        when(conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(any(UUID.class), eq("WHATSAPP")))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(OmnichannelConversation.class))).thenAnswer(invocation -> {
            OmnichannelConversation conversation = invocation.getArgument(0);
            conversation.prePersist();
            return conversation;
        });
        when(messageRepository.save(any(OmnichannelConversationMessage.class))).thenAnswer(invocation -> {
            OmnichannelConversationMessage message = invocation.getArgument(0);
            message.prePersist();
            return message;
        });

        var response = service.captureLead(request);

        assertNotNull(response.getId());
        assertEquals(response.getId(), response.getLeadId());
        assertEquals("WHATSAPP", response.getChannel());
        assertEquals("919175834000", response.getExternalUserId());
        assertEquals("+91 9175834000", response.getMobile());
        assertEquals("Wedding Lead Campaign", response.getSourceCampaign());
        assertEquals("occasion=wedding; budget=2000; language=hinglish", response.getProductInterest());
        assertEquals("Show pearl earrings under 2000", response.getLatestMessage());

        ArgumentCaptor<OmnichannelConversation> conversationCaptor = ArgumentCaptor.forClass(OmnichannelConversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertEquals("wamid.demo", conversationCaptor.getValue().getExternalThreadId());

        ArgumentCaptor<OmnichannelConversationMessage> messageCaptor = ArgumentCaptor.forClass(OmnichannelConversationMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertEquals("Show pearl earrings under 2000", messageCaptor.getValue().getMessageText());
    }
}
