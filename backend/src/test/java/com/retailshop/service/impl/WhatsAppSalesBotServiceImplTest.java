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
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.MarketingChannelResult;
import com.retailshop.service.OmnichannelCommerceService;
import com.retailshop.service.WhatsAppMessageService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppSalesBotServiceImplTest {

    @Mock
    private OmnichannelCommerceService omnichannelCommerceService;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OmnichannelLeadRepository leadRepository;

    @Mock
    private OmnichannelConversationRepository conversationRepository;

    @Mock
    private OmnichannelConversationMessageRepository messageRepository;

    @Mock
    private WhatsAppMessageService whatsAppMessageService;

    private WhatsAppSalesBotServiceImpl service;
    private UUID leadId;

    @BeforeEach
    void setUp() {
        MarketingProperties properties = new MarketingProperties();
        properties.getAi().setEnabled(false);
        service = new WhatsAppSalesBotServiceImpl(
                properties,
                omnichannelCommerceService,
                orderRepository,
                offerRepository,
                productRepository,
                leadRepository,
                conversationRepository,
                messageRepository,
                whatsAppMessageService,
                new ObjectMapper()
        );
        leadId = UUID.randomUUID();
        lenient().when(whatsAppMessageService.sendText(any(), any())).thenReturn(MarketingChannelResult.builder()
                .success(false)
                .errorMessage("WhatsApp sender is not configured")
                .build());
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
        assertTrue(response.getReplyText().contains("Reply BUY"));
        assertFalse(response.getReplyText().contains("autoAdd"));
        assertTrue(response.getErrorMessage().contains("WhatsApp sender is not configured"));

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
    void shouldUnderstandMisspelledCategoryAndFallbackToCatalogSearch() {
        mockLeadCapture("Customer");
        Product necklace = product("Classic Pearl Necklace", "NECKALACE");
        necklace.setQuantity(0);
        when(productRepository.findAll()).thenReturn(List.of(necklace));
        when(omnichannelCommerceService.searchProducts(any(OmnichannelProductSearchRequest.class))).thenReturn(
                OmnichannelProductSearchResponse.builder()
                        .query("neckalce")
                        .totalMatches(0)
                        .products(List.of())
                        .build()
        );
        mockOutboundConversation();

        var response = service.handleWebhook("""
                {"from":"918390968506","name":"Customer","text":"show neckalce","messageId":"wamid.typo"}
                """, null);

        assertTrue(response.isAccepted());
        assertFalse(response.isSent());
        assertEquals(1, response.getProductCount());
        assertTrue(response.getReplyText().contains("Classic Pearl Necklace"));
        assertTrue(response.getReplyText().contains("Out of stock"));
        assertTrue(response.getReplyText().contains("Reply BUY"));
        assertFalse(response.getReplyText().contains("utm_"));

        ArgumentCaptor<OmnichannelProductSearchRequest> searchCaptor = ArgumentCaptor.forClass(OmnichannelProductSearchRequest.class);
        verify(omnichannelCommerceService).searchProducts(searchCaptor.capture());
        assertEquals("NECKALACE", searchCaptor.getValue().getCategory());
        assertTrue(searchCaptor.getValue().getQuery().contains("necklace"));
        assertFalse(searchCaptor.getValue().getInStockOnly());
    }

    @Test
    void shouldSendFirstProductImageWhenReplyHasProductVisual() {
        mockLeadCapture("Customer");
        when(productRepository.findAll()).thenReturn(List.of(product("Necklace SB", "NECKALACE")));
        when(omnichannelCommerceService.searchProducts(any(OmnichannelProductSearchRequest.class))).thenReturn(
                OmnichannelProductSearchResponse.builder()
                        .query("necklace")
                        .totalMatches(1)
                        .products(List.of(OmnichannelProductCardResponse.builder()
                                .productId(UUID.randomUUID())
                                .name("Necklace SB")
                                .category("NECKALACE")
                                .price(BigDecimal.valueOf(12))
                                .stockLabel("Available now")
                                .shortBenefit("Ready-to-order style")
                                .productUrl("https://kpskrishnai.com/products?productId=demo")
                                .buyNowUrl("https://kpskrishnai.com/products?autoAdd=demo")
                                .imageUrl("https://kpskrishnai.com/api/images/products/necklace.png")
                                .build()))
                        .build()
        );
        when(whatsAppMessageService.sendImage(any(), any(), any())).thenReturn(MarketingChannelResult.builder()
                .success(true)
                .responseId("image-1")
                .build());
        mockOutboundConversation();

        var response = service.handleWebhook("""
                {"from":"918390968506","name":"Customer","text":"show necklace","messageId":"wamid.visual"}
                """, null);

        assertTrue(response.isSent());
        assertEquals("image-1", response.getProviderMessageId());

        ArgumentCaptor<String> imageUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppMessageService).sendImage(any(), imageUrlCaptor.capture(), captionCaptor.capture());
        verify(whatsAppMessageService, never()).sendText(any(), any());
        assertEquals("https://kpskrishnai.com/api/images/products/necklace.png", imageUrlCaptor.getValue());
        assertTrue(captionCaptor.getValue().contains("Necklace SB"));
        assertTrue(captionCaptor.getValue().contains("₹12"));
        assertFalse(captionCaptor.getValue().contains("autoAdd"));
        assertFalse(captionCaptor.getValue().contains("utm_"));
    }

    @Test
    void shouldAnswerGreetingWithAvailableCategories() {
        mockLeadCapture("Customer");
        mockOutboundConversation();

        var response = service.handleWebhook("""
                {"from":"919175834000","name":"Customer","text":"Namaste","messageId":"wamid.test2"}
                """, null);

        assertTrue(response.isAccepted());
        assertFalse(response.isSent());
        assertEquals(0, response.getProductCount());
        assertTrue(response.getReplyText().contains("Shop Products"));
        assertTrue(response.getReplyText().contains("My Orders"));
        assertTrue(response.getReplyText().contains("Connect to Agent"));
        verify(omnichannelCommerceService, never()).searchProducts(any());
    }

    @Test
    void shouldRouteNumericMenuChoices() {
        mockLeadCapture("Customer");
        mockOutboundConversation();

        var categoryResponse = service.handleWebhook("""
                {"from":"919175834000","name":"Customer","text":"2","messageId":"wamid.menu2"}
                """, null);

        assertTrue(categoryResponse.isAccepted());
        assertTrue(categoryResponse.getReplyText().contains("Choose a category"));
        verify(omnichannelCommerceService, never()).searchProducts(any());

        var paymentResponse = service.handleWebhook("""
                {"from":"919175834000","name":"Customer","text":"5","messageId":"wamid.menu5"}
                """, null);

        assertTrue(paymentResponse.isAccepted());
        assertTrue(paymentResponse.getReplyText().contains("payment"));
        assertTrue(paymentResponse.getReplyText().contains("order number"));
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
