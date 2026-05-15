package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.Customer;
import com.retailshop.service.MarketingChannelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WhatsAppMessageServiceImplTest {

    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getMeta().setAccessToken("meta-token");
        appProperties.getMeta().setWhatsappPhoneNumberId("1011524478720219");
        appProperties.getMeta().setWhatsappOtpTemplateName("login_otp");
        appProperties.getMeta().setWhatsappOtpTemplateLanguage("en_US");
        objectMapper = new ObjectMapper();
        httpClient = mock(HttpClient.class);
    }

    @Test
    void shouldSendOtpUsingMetaTemplateWhenConfigured() throws Exception {
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);
        HttpResponse<String> sendResponse = mockResponse(200, """
                {"messages":[{"id":"wamid.123"}]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertTrue(result.isSuccess());
        assertEquals("wamid.123", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://graph.facebook.com/v23.0/1011524478720219/messages", request.uri().toString());
        String body = readBody(request);
        assertTrue(body.contains("\"type\":\"template\""));
        assertTrue(body.contains("\"name\":\"login_otp\""));
        assertTrue(body.contains("\"text\":\"123456\""));
        assertTrue(body.contains("\"sub_type\":\"url\""));
        assertTrue(body.contains("\"index\":\"0\""));
        assertTrue(body.contains("\"to\":\"918390968506\""));
    }

    @Test
    void shouldFailOtpWhenTemplateIsNotConfigured() {
        appProperties.getMeta().setWhatsappOtpTemplateName("");
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertEquals(false, result.isSuccess());
        assertEquals("WhatsApp OTP template is not configured for the selected sender phone", result.getErrorMessage());
        verifyNoInteractions(httpClient);
    }

    @Test
    void shouldSendOtpUsingGupshupTemplateWhenConfigured() throws Exception {
        appProperties.setWhatsappProvider("GUPSHUP");
        appProperties.getGupshup().setApiKey("gupshup-key");
        appProperties.getGupshup().setSourceNumber("918830461523");
        appProperties.getGupshup().setAppName("KPSKrishnai");
        appProperties.getGupshup().getTemplates().setOtp("20f18cc4-778a-4462-90d6-98faec857ed3");
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);
        HttpResponse<String> sendResponse = mockResponse(202, """
                {"status":"submitted","messageId":"gupshup-msg-123"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertTrue(result.isSuccess());
        assertEquals("gupshup-msg-123", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://api.gupshup.io/wa/api/v1/template/msg", request.uri().toString());
        String body = URLDecoder.decode(readBody(request), StandardCharsets.UTF_8);
        assertTrue(body.contains("channel=whatsapp"));
        assertTrue(body.contains("source=918830461523"));
        assertTrue(body.contains("destination=918390968506"));
        assertTrue(body.contains("src.name=KPSKrishnai"));
        assertTrue(body.contains("\"id\":\"20f18cc4-778a-4462-90d6-98faec857ed3\""));
        assertTrue(body.contains("\"params\":[\"123456\",\"Login\",\"123456\",\"Login\"]"));
    }

    @Test
    void shouldSendImageUsingGupshupMessageApiWhenConfigured() throws Exception {
        appProperties.setWhatsappProvider("GUPSHUP");
        appProperties.getGupshup().setApiKey("gupshup-key");
        appProperties.getGupshup().setSourceNumber("918830461523");
        appProperties.getGupshup().setAppName("KPSKrishnai");
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);
        HttpResponse<String> sendResponse = mockResponse(202, """
                {"status":"submitted","messageId":"gupshup-image-123"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sendResponse);

        MarketingChannelResult result = service.sendImage("8390968506", "https://kpskrishnai.com/api/images/products/demo.png", "Pearl Earrings");

        assertTrue(result.isSuccess());
        assertEquals("gupshup-image-123", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://api.gupshup.io/wa/api/v1/msg", request.uri().toString());
        String body = URLDecoder.decode(readBody(request), StandardCharsets.UTF_8);
        assertTrue(body.contains("destination=918390968506"));
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"originalUrl\":\"https://kpskrishnai.com/api/images/products/demo.png\""));
        assertTrue(body.contains("\"previewUrl\":\"https://kpskrishnai.com/api/images/products/demo.png\""));
        assertTrue(body.contains("\"caption\":\"Pearl Earrings\""));
    }

    @Test
    void shouldBroadcastCampaignToEveryUniqueWhatsAppRecipientAndReportFailures() throws Exception {
        appProperties.setWhatsappProvider("GUPSHUP");
        appProperties.getGupshup().setApiKey("gupshup-key");
        appProperties.getGupshup().setSourceNumber("918830461523");
        appProperties.getGupshup().setAppName("KPSKrishnai");
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);
        HttpResponse<String> firstResponse = mockResponse(202, """
                {"status":"submitted","messageId":"gupshup-1"}
                """);
        HttpResponse<String> secondResponse = mockResponse(400, """
                {"message":"Invalid destination"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);
        Campaign campaign = new Campaign();
        campaign.setContent("Gudi Padwa Offer");
        campaign.setMediaUrl("/api/images/marketing-campaigns/offer.png");
        Customer first = customer("+91 9175834000");
        Customer duplicate = customer("9175834000");
        Customer second = customer("8390968506");

        MarketingChannelResult result = service.publishCampaign(campaign, List.of(first, duplicate, second));

        assertEquals(false, result.isSuccess());
        assertEquals(2, result.getTotalRecipients());
        assertEquals(1, result.getSentCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getDeliveryReport().contains("919175834000=SENT"));
        assertTrue(result.getDeliveryReport().contains("918390968506=FAILED"));
        assertTrue(result.getErrorMessage().contains("Invalid destination"));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = requestCaptor.getAllValues();
        String firstBody = URLDecoder.decode(readBody(requests.get(0)), StandardCharsets.UTF_8);
        String secondBody = URLDecoder.decode(readBody(requests.get(1)), StandardCharsets.UTF_8);
        assertTrue(firstBody.contains("destination=919175834000"));
        assertTrue(secondBody.contains("destination=918390968506"));
        assertTrue(firstBody.contains("\"originalUrl\":\"https://kpskrishnai.com/api/images/marketing-campaigns/offer.png\""));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private String readBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });
        completed.get(3, TimeUnit.SECONDS);
        return output.toString(StandardCharsets.UTF_8);
    }

    private Customer customer(String mobile) {
        Customer customer = new Customer();
        customer.setMobile(mobile);
        return customer;
    }
}
