package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import com.retailshop.entity.Campaign;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialMediaServiceImplTest {

    @Test
    void shouldPublishInstagramWithPageAccessToken() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getMeta().setAccessToken("generic-meta-token");
        properties.getMeta().setPageAccessToken("facebook-page-token");
        properties.getMeta().setInstagramBusinessAccountId("17841420528620057");
        properties.getMeta().setGraphVersion("v25.0");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> containerResponse = mockResponse(200, "{\"id\":\"container-1\"}");
        HttpResponse<String> containerStatusResponse = mockResponse(200, "{\"status_code\":\"FINISHED\"}");
        HttpResponse<String> publishResponse = mockResponse(200, "{\"id\":\"instagram-post-1\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(containerResponse)
                .thenReturn(containerStatusResponse)
                .thenReturn(publishResponse);

        SocialMediaServiceImpl service = new SocialMediaServiceImpl(properties, new ObjectMapper(), httpClient);
        Campaign campaign = new Campaign();
        campaign.setContent("Caption line");
        campaign.setHashtags("#Retail");
        campaign.setMediaUrl("https://kpskrishnai.com/api/images/marketing-campaigns/image.png");

        var result = service.publishInstagram(campaign);

        assertTrue(result.isSuccess());
        assertEquals("instagram-post-1", result.getResponseId());
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(3)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = captor.getAllValues();
        assertEquals("https://graph.facebook.com/v25.0/17841420528620057/media", requests.get(0).uri().toString());
        assertTrue(requests.get(1).uri().toString().startsWith("https://graph.facebook.com/v25.0/container-1?"));
        assertEquals("https://graph.facebook.com/v25.0/17841420528620057/media_publish", requests.get(2).uri().toString());
        String createBody = readBody(requests.get(0));
        String publishBody = readBody(requests.get(2));
        assertTrue(createBody.contains("access_token=facebook-page-token"));
        assertTrue(publishBody.contains("access_token=facebook-page-token"));
        assertFalse(createBody.contains("generic-meta-token"));
        assertTrue(createBody.contains("image_url=https%3A%2F%2Fkpskrishnai.com%2Fapi%2Fimages%2Fmarketing-campaigns%2Fimage.png"));
        assertTrue(createBody.contains("Visit+now%3A+https%3A%2F%2Fkpskrishnai.com"));
        assertTrue(publishBody.contains("creation_id=container-1"));
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
