package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.OmnichannelLeadRequest;
import com.retailshop.dto.OmnichannelLeadResponse;
import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.dto.OmnichannelProductSearchRequest;
import com.retailshop.dto.OmnichannelProductSearchResponse;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Product;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.OmnichannelCommerceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppSalesBotServiceImplTest {

    @Mock
    private OmnichannelCommerceService omnichannelCommerceService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OmnichannelLeadRepository leadRepository;

    @Mock
    private OmnichannelConversationRepository conversationRepository;

    @Mock
    private OmnichannelConversationMessageRepository messageRepository;

    private WhatsAppSalesBotServiceImpl service;
    private UUID leadId;

    @BeforeEach
    void setUp() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        service = new WhatsAppSalesBotServiceImpl(
                properties,
                omnichannelCommerceService,
                productRepository,
                leadRepository,
                conversationRepository,
                messageRepository,
                new ObjectMapper()
        );
        leadId = UUID.randomUUID();
    }

    @Test
    void shouldShowProductsForCategoryAndBudgetQuery() {
        mockLeadCapture("Aarti");
        when(productRepository.findAll()).thenReturn(List.of(product("Pearl Earrings", "Earrings")));
        when(omnichannelCommerceService.searchProducts(any(OmnichannelProductSearchRequest.class))).thenReturn(
                OmnichannelProductSearchResponse.builder()
                        .query("earrings")
                        .totalMatches(1)
                        .introMessage("Here are options")
                        .products(List.of(OmnichannelProductCardResponse.builder()
                                .productId(UUID.randomUUID())
                                .name("Pearl Earrings")
                                .category("Earrings")
                                .price(BigDecimal.valueOf(1499))
                                .stockLabel("Available now")
                                .shortBenefit("Classic pearl style for gifting")
                                .buyNowUrl("https://kpskrishnai.com/products?autoAdd=demo")
                                .build()))
                        .build()
        );
        mockOutboundConversation();

        var response = service.handleWebhook("""
                {"from":"919175834000","name":"Aarti","text":"Show earrings under 2000","messageId":"wamid.test"}
                """, null);

        assertTrue(response.isAccepted());
        assertFalse(response.isSent());
        assertEquals(1, response.getProductCount());
        assertTrue(response.getReplyText().contains("Pearl Earrings"));
        assertTrue(response.getReplyText().contains("Buy Now"));
        assertTrue(response.getErrorMessage().contains("Gupshup bot sender needs"));

        ArgumentCaptor<OmnichannelProductSearchRequest> searchCaptor = ArgumentCaptor.forClass(OmnichannelProductSearchRequest.class);
        verify(omnichannelCommerceService).searchProducts(searchCaptor.capture());
        assertEquals("Earrings", searchCaptor.getValue().getCategory());
        assertEquals(BigDecimal.valueOf(2000), searchCaptor.getValue().getMaxPrice());
        assertEquals("whatsapp", searchCaptor.getValue().getSource());

        ArgumentCaptor<OmnichannelConversationMessage> outboundCaptor = ArgumentCaptor.forClass(OmnichannelConversationMessage.class);
        verify(messageRepository).save(outboundCaptor.capture());
        assertEquals("OUTBOUND", outboundCaptor.getValue().getDirection());
        assertTrue(outboundCaptor.getValue().getMessageText().contains("Pearl Earrings"));
    }

    @Test
    void shouldAnswerGreetingWithAvailableCategories() {
        mockLeadCapture("Customer");
        when(productRepository.findAll()).thenReturn(List.of(
                product("Pearl Earrings", "Earrings"),
                product("Matte Lipstick", "Cosmetics")
        ));
        mockOutboundConversation();

        var response = service.handleWebhook("""
                {"payload":{"source":"919175834000","sender":{"name":"Customer"},"payload":{"text":"Namaste"}}}
                """, null);

        assertTrue(response.isAccepted());
        assertFalse(response.isSent());
        assertEquals(0, response.getProductCount());
        assertTrue(response.getReplyText().contains("Available categories"));
        assertTrue(response.getReplyText().contains("Earrings"));
        assertTrue(response.getReplyText().contains("Cosmetics"));
        verify(omnichannelCommerceService, never()).searchProducts(any());
    }

    private void mockLeadCapture(String customerName) {
        when(omnichannelCommerceService.captureLead(any(OmnichannelLeadRequest.class))).thenReturn(
                OmnichannelLeadResponse.builder()
                        .id(leadId)
                        .leadId(leadId)
                        .channel("WHATSAPP")
                        .externalUserId("919175834000")
                        .customerName(customerName)
                        .mobile("+91 9175834000")
                        .status("NEW")
                        .build()
        );
    }

    private void mockOutboundConversation() {
        OmnichannelLead lead = new OmnichannelLead();
        lead.setId(leadId);
        lead.setChannel("WHATSAPP");
        lead.setStatus("NEW");
        OmnichannelConversation conversation = new OmnichannelConversation();
        conversation.setId(UUID.randomUUID());
        conversation.setLead(lead);
        conversation.setChannel("WHATSAPP");
        conversation.setStatus("OPEN");
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(leadId, "WHATSAPP")).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(OmnichannelConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Product product(String name, String category) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setCategory(category);
        product.setSku(name.toUpperCase().replaceAll("[^A-Z0-9]+", "-"));
        product.setSellingPrice(BigDecimal.valueOf(1000));
        product.setQuantity(10);
        return product;
    }
}
