package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.MarketingProperties;
import com.retailshop.dto.ImageUploadResponse;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.service.ImageUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryOptionServiceImplTest {

    @Mock
    private ProductCategoryOptionRepository productCategoryOptionRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Test
    void shouldGenerateCategoryIconWithMarketingOpenAiConfigAndUploadResult() throws Exception {
        MarketingProperties marketingProperties = marketingProperties();
        CapturingHttpClient httpClient = new CapturingHttpClient(openAiImageResponse());
        when(imageUploadService.uploadImageBytes(any(), eq("image/png"), eq("category-icons")))
                .thenReturn(ImageUploadResponse.builder()
                        .id(UUID.randomUUID())
                        .category("category-icons")
                        .cloudfrontUrl("https://cdn.example.com/category-icons/bali.png")
                        .build());
        ProductCategoryOptionServiceImpl service = new ProductCategoryOptionServiceImpl(
                productCategoryOptionRepository,
                marketingProperties,
                new ObjectMapper(),
                imageUploadService,
                httpClient
        );

        var options = service.generateIconOptions("Bali Mens");

        assertEquals(1, options.size());
        assertEquals("OpenAI Icon", options.get(0).getLabel());
        assertEquals("https://cdn.example.com/category-icons/bali.png", options.get(0).getImageUrl());
        assertEquals(URI.create("https://api.openai.com/v1/images/generations"), httpClient.request.uri());
        assertEquals(Optional.of("Bearer test-openai-key"), httpClient.request.headers().firstValue("authorization"));
        String body = requestBody(httpClient.request);
        assertTrue(body.contains("\"model\":\"gpt-image-1.5\""));
        assertTrue(body.contains("\"background\":\"transparent\""));
        assertTrue(body.contains("Category: Bali Mens."));
        assertTrue(body.contains("no text, no letters, no logo, no watermark"));
        verify(imageUploadService).uploadImageBytes(any(), eq("image/png"), eq("category-icons"));
    }

    @Test
    void shouldRejectIconGenerationWhenMarketingOpenAiConfigIsMissing() {
        MarketingProperties marketingProperties = new MarketingProperties();
        marketingProperties.getAi().setApiKey("");
        ProductCategoryOptionServiceImpl service = new ProductCategoryOptionServiceImpl(
                productCategoryOptionRepository,
                marketingProperties,
                new ObjectMapper(),
                imageUploadService,
                new CapturingHttpClient("{}")
        );

        assertThrows(BusinessException.class, () -> service.generateIconOptions("Bali Mens"));
        verify(imageUploadService, never()).uploadImageBytes(any(), any(), any());
    }

    private MarketingProperties marketingProperties() {
        MarketingProperties marketingProperties = new MarketingProperties();
        marketingProperties.getAi().setEnabled(true);
        marketingProperties.getAi().setApiKey("test-openai-key");
        marketingProperties.getAi().setImageModel("gpt-image-1.5");
        marketingProperties.getAi().setImageSize("1024x1024");
        marketingProperties.getAi().setImageQuality("medium");
        return marketingProperties;
    }

    private String openAiImageResponse() throws IOException {
        return "{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(testPng()) + "\"}]}";
    }

    private byte[] testPng() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color(255, 220, 128, 255).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String requestBody(HttpRequest request) throws Exception {
        var publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        done.await(2, TimeUnit.SECONDS);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static class CapturingHttpClient extends HttpClient {
        private final String responseBody;
        private HttpRequest request;

        private CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            return (HttpResponse<T>) new StringResponse(request, responseBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record StringResponse(HttpRequest request, String body) implements HttpResponse<String> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
