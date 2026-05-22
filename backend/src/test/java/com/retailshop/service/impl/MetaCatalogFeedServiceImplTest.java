package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.entity.Product;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaCatalogFeedServiceImplTest {

    @Mock
    private ReceiptSettingsRepository receiptSettingsRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryOptionRepository productCategoryOptionRepository;

    @Mock
    private OfferRepository offerRepository;

    private MetaCatalogFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MetaCatalogFeedServiceImpl(
                receiptSettingsRepository,
                productRepository,
                productCategoryOptionRepository,
                offerRepository,
                new ObjectMapper()
        );
    }

    @Test
    void shouldIncludeAdditionalImagesAndCatalogLabelsInXmlFeed() {
        ReceiptSettings settings = new ReceiptSettings();
        settings.setFacebookCatalogEnabled(true);
        settings.setFacebookFeedToken("feed-token");

        ProductCategoryOption category = new ProductCategoryOption();
        category.setCode("NECKLACE");
        category.setDisplayName("Necklaces");
        category.prePersist();

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Pearl Necklace");
        product.setCategory("NECKLACE");
        product.setSku("NK001");
        product.setCostPrice(BigDecimal.valueOf(500));
        product.setSellingPrice(BigDecimal.valueOf(1200));
        product.setQuantity(7);
        product.setLowStockThreshold(2);
        product.setProductImagesJson("[\"/images/primary.webp\",\"/images/side.webp\",\"https://cdn.example.com/detail.webp\"]");
        product.setCreatedAt(LocalDateTime.now());
        product.prePersist();

        when(receiptSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(productCategoryOptionRepository.findAll()).thenReturn(List.of(category));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(offerRepository.findActiveOffers(LocalDate.now())).thenReturn(List.of());

        String xml = service.xmlFeed("feed-token");

        assertTrue(xml.contains("<g:image_link>https://kpskrishnai.com/images/primary.webp</g:image_link>"));
        assertTrue(xml.contains("<g:additional_image_link>https://kpskrishnai.com/images/side.webp</g:additional_image_link>"));
        assertTrue(xml.contains("<g:additional_image_link>https://cdn.example.com/detail.webp</g:additional_image_link>"));
        assertTrue(xml.contains("<g:inventory>7</g:inventory>"));
        assertTrue(xml.contains("<g:custom_label_0>Necklaces</g:custom_label_0>"));
        assertTrue(xml.contains("<g:custom_label_2>website</g:custom_label_2>"));
    }
}
