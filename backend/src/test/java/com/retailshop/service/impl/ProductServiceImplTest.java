package com.retailshop.service.impl;

import com.retailshop.dto.ProductRequest;
import com.retailshop.entity.Product;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.OrderItemRepository;
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
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryOptionService productCategoryOptionService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(invoiceItemRepository, orderItemRepository, productRepository, productCategoryOptionService);
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

    @Test
    void shouldRankTrendingProductsFromPaidOrdersAndKeepPublicResultsInStock() {
        Product bestSeller = new Product();
        bestSeller.setId(UUID.randomUUID());
        bestSeller.setName("Temple Necklace");
        bestSeller.setCategory("JEWELLERY");
        bestSeller.setSku("JEW-TEMP-001");
        bestSeller.setSellingPrice(BigDecimal.valueOf(1200));
        bestSeller.setQuantity(8);
        bestSeller.setLowStockThreshold(2);
        bestSeller.setCreatedAt(LocalDateTime.now().minusDays(1));
        bestSeller.prePersist();

        Product soldOut = new Product();
        soldOut.setId(UUID.randomUUID());
        soldOut.setName("Pearl Drop Earrings");
        soldOut.setCategory("JEWELLERY");
        soldOut.setSku("JEW-PRL-001");
        soldOut.setSellingPrice(BigDecimal.valueOf(699));
        soldOut.setQuantity(0);
        soldOut.setLowStockThreshold(2);
        soldOut.setCreatedAt(LocalDateTime.now().minusDays(2));
        soldOut.prePersist();

        Product fallback = new Product();
        fallback.setId(UUID.randomUUID());
        fallback.setName("Daily Pearl Studs");
        fallback.setCategory("JEWELLERY");
        fallback.setSku("JEW-STD-001");
        fallback.setSellingPrice(BigDecimal.valueOf(499));
        fallback.setQuantity(6);
        fallback.setLowStockThreshold(2);
        fallback.setCreatedAt(LocalDateTime.now());
        fallback.prePersist();

        when(productRepository.findAll()).thenReturn(List.of(bestSeller, soldOut, fallback));
        when(orderItemRepository.findTrendingProductSales(any())).thenReturn(List.of(
                new Object[]{soldOut.getId(), 14L},
                new Object[]{bestSeller.getId(), 11L}
        ));

        var trendingProducts = productService.getTrendingProducts(2);
        var publicTrending = productService.getPublicTrendingProducts(2);

        assertEquals(List.of(soldOut.getId(), bestSeller.getId()), trendingProducts.stream().map(product -> product.getId()).toList());
        assertEquals(List.of(bestSeller.getId(), fallback.getId()), publicTrending.stream().map(product -> product.getId()).toList());
    }
}
