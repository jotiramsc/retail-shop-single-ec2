package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.service.MarketingChannelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppMessageServiceImplTest {

    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTwilio().setAccountSid("AC123");
        appProperties.getTwilio().setAuthToken("secret");
        appProperties.getTwilio().setWhatsappFrom("whatsapp:+14155238886");
        objectMapper = new ObjectMapper();
        httpClient = mock(HttpClient.class);
    }

    @Test
    void shouldUseAuthenticationTemplateForOtpWhenTwilioTemplateIsAvailable() throws Exception {
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);

        HttpResponse<String> contentResponse = mockResponse(200, """
                {"contents":[{"sid":"HX123","friendly_name":"verifications_2fa_template","types":["whatsapp/authentication"]}]}
                """);
        HttpResponse<String> sendResponse = mockResponse(201, """
                {"sid":"SM123"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(contentResponse, sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertTrue(result.isSuccess());
        assertEquals("SM123", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertEquals("https://content.twilio.com/v1/Content", requests.get(0).uri().toString());

        String formBody = readBody(requests.get(1));
        assertTrue(formBody.contains("ContentSid=HX123"));
        assertTrue(formBody.contains("ContentVariables=%7B%221%22%3A%22123456%22%7D"));
        assertFalse(formBody.contains("Body="));
    }

    @Test
    void shouldRejectSandboxOtpWhenNoTemplateCanBeResolved() throws Exception {
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);

        HttpResponse<String> contentResponse = mockResponse(200, """
                {"contents":[]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(contentResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertFalse(result.isSuccess());
        assertEquals("Twilio WhatsApp OTP template is not configured", result.getErrorMessage());
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldFallbackToPlainBodyForNonSandboxSenderWhenNoTemplateExists() throws Exception {
        appProperties.getTwilio().setWhatsappFrom("whatsapp:+919999999999");
        WhatsAppMessageServiceImpl service = new WhatsAppMessageServiceImpl(appProperties, objectMapper, httpClient);

        HttpResponse<String> contentResponse = mockResponse(200, """
                {"contents":[]}
                """);
        HttpResponse<String> sendResponse = mockResponse(201, """
                {"sid":"SM456"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(contentResponse, sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertTrue(result.isSuccess());
        assertEquals("SM456", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = requestCaptor.getAllValues();
        String formBody = readBody(requests.get(1));
        assertTrue(formBody.contains("Body=Your+login+code+is+123456."));
        assertFalse(formBody.contains("ContentSid="));
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
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                this.subscription.request(Long.MAX_VALUE);
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
}
