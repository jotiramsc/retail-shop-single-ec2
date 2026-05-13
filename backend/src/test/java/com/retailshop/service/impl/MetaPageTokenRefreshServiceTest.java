package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaPageTokenRefreshServiceTest {

    @Test
    void shouldExchangeUserTokenAndRefreshPageAccessTokenInMemory() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getMeta().setGraphVersion("v23.0");
        properties.getMeta().setAppId("app-id");
        properties.getMeta().setAppSecret("app-secret");
        properties.getMeta().setExchangeToken("old-user-token");
        properties.getMeta().setPageId("102231779241745");
        properties.getMeta().setTokenRefreshSecretId("");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> exchangeResponse = mockResponse(200, "{\"access_token\":\"new-long-user-token\"}");
        HttpResponse<String> pageResponse = mockResponse(200, "{\"access_token\":\"new-page-token\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(exchangeResponse)
                .thenReturn(pageResponse);

        MetaPageTokenRefreshService service = new MetaPageTokenRefreshService(
                properties,
                new ObjectMapper(),
                httpClient,
                null
        );

        service.refreshIfConfigured();

        assertEquals("new-long-user-token", properties.getMeta().getAccessToken());
        assertEquals("new-long-user-token", properties.getMeta().getExchangeToken());
        assertEquals("new-page-token", properties.getMeta().getPageAccessToken());

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, org.mockito.Mockito.times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = captor.getAllValues();
        assertTrue(requests.get(0).uri().toString().contains("/v23.0/oauth/access_token"));
        assertTrue(requests.get(0).uri().toString().contains("fb_exchange_token=old-user-token"));
        assertEquals("https://graph.facebook.com/v23.0/102231779241745?fields=access_token&access_token=new-long-user-token",
                requests.get(1).uri().toString());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }
}
