package com.retailshop.service.impl;

import com.retailshop.dto.ProductRequest;
import com.retailshop.entity.Product;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.ProductCategoryOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryOptionService productCategoryOptionService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(invoiceItemRepository, productRepository, productCategoryOptionService);
    }

    @Test
    void shouldCalculateWebsitePriceFromShopPricePercentage() {
        ProductRequest request = new ProductRequest();
        request.setName("Temple Necklace");
        request.setCategory("JEWELLERY");
        request.setSku("JEW-TEMP-001");
        request.setCostPrice(BigDecimal.valueOf(80));
        request.setSellingPrice(BigDecimal.valueOf(100));
        request.setWebsitePricePercentage(BigDecimal.TEN);
        request.setQuantity(5);
        request.setLowStockThreshold(2);

        when(productRepository.findBySku("JEW-TEMP-001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.prePersist();
            return saved;
        });

        var response = productService.createProduct(request);

        assertEquals(BigDecimal.valueOf(100.00).setScale(2), response.getSellingPrice());
        assertEquals(BigDecimal.valueOf(10.00).setScale(2), response.getWebsitePricePercentage());
        assertEquals(BigDecimal.valueOf(110.00).setScale(2), response.getWebsitePrice());
    }

    @Test
    void shouldFallbackWebsitePriceAndExposeStockLabelsInPublicCatalog() {
        Product outOfStock = new Product();
        outOfStock.setId(UUID.randomUUID());
        outOfStock.setName("Pearl Ring");
        outOfStock.setCategory("JEWELLERY");
        outOfStock.setSku("JEW-PR-001");
        outOfStock.setCostPrice(BigDecimal.valueOf(40));
        outOfStock.setSellingPrice(BigDecimal.valueOf(100));
        outOfStock.setQuantity(0);
        outOfStock.setLowStockThreshold(3);
        outOfStock.setCreatedAt(LocalDateTime.now().minusDays(1));
        outOfStock.prePersist();

        Product lowStock = new Product();
        lowStock.setId(UUID.randomUUID());
        lowStock.setName("Gold Bangle");
        lowStock.setCategory("JEWELLERY");
        lowStock.setSku("JEW-GB-001");
        lowStock.setCostPrice(BigDecimal.valueOf(60));
        lowStock.setSellingPrice(BigDecimal.valueOf(200));
        lowStock.setQuantity(2);
        lowStock.setLowStockThreshold(4);
        lowStock.setWebsitePricePercentage(null);
        lowStock.setCreatedAt(LocalDateTime.now());
        lowStock.prePersist();

        when(productRepository.findAll()).thenReturn(List.of(outOfStock, lowStock));

        var catalog = productService.getPublicCatalog();

        assertEquals(2, catalog.size());
        assertEquals("Last few remaining", catalog.get(0).getStockLabel());
        assertTrue(catalog.get(0).getInStock());
        assertEquals(BigDecimal.valueOf(200.00).setScale(2), catalog.get(0).getSellingPrice());

        assertEquals("Out of stock", catalog.get(1).getStockLabel());
        assertFalse(catalog.get(1).getInStock());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), catalog.get(1).getSellingPrice());
        assertNull(catalog.get(1).getImageDataUrl());
    }
}
