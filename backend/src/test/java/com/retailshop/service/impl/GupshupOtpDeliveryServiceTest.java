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
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GupshupOtpDeliveryServiceTest {

    private AppProperties appProperties;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getGupshup().setApiKey("api-key");
        appProperties.getGupshup().setAppName("krishnaiapp");
        appProperties.getGupshup().setSourceNumber("919999999999");
        appProperties.getGupshup().setOtpTemplateId("template-123");
        objectMapper = new ObjectMapper();
        httpClient = mock(HttpClient.class);
    }

    @Test
    void shouldSendAuthenticationTemplateOtpThroughGupshup() throws Exception {
        GupshupOtpDeliveryService service = new GupshupOtpDeliveryService(appProperties, objectMapper, httpClient);
        HttpResponse<String> sendResponse = mockResponse(202, """
                {"status":"submitted","messageId":"msg-123"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertTrue(result.isSuccess());
        assertEquals("msg-123", result.getResponseId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        String formBody = readBody(requestCaptor.getValue());
        assertTrue(formBody.contains("channel=whatsapp"));
        assertTrue(formBody.contains("source=919999999999"));
        assertTrue(formBody.contains("destination=918390968506"));
        assertTrue(formBody.contains("src.name=krishnaiapp"));
        String decodedFormBody = URLDecoder.decode(formBody, StandardCharsets.UTF_8);
        assertTrue(decodedFormBody.contains("template={"));
        assertTrue(decodedFormBody.contains("\"id\":\"template-123\""));
        assertTrue(decodedFormBody.contains("\"params\":[\"123456\"]"));
    }

    @Test
    void shouldReturnHelpfulErrorWhenProviderRejectsOtp() throws Exception {
        GupshupOtpDeliveryService service = new GupshupOtpDeliveryService(appProperties, objectMapper, httpClient);
        HttpResponse<String> sendResponse = mockResponse(401, """
                {"status":"error","message":{"message":"Authentication Failed"}}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(sendResponse);

        MarketingChannelResult result = service.sendOtp("8390968506", "123456", 5);

        assertFalse(result.isSuccess());
        assertEquals("Authentication Failed", result.getErrorMessage());
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
}
