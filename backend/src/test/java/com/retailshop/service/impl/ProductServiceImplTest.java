package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.dto.ProductRequest;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.enums.OfferType;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.OrderItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import com.retailshop.service.ProductAiDescriptionService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private OfferRepository offerRepository;

    @Mock
    private ReceiptSettingsRepository receiptSettingsRepository;

    @Mock
    private ProductCategoryOptionService productCategoryOptionService;

    @Mock
    private ProductAiDescriptionService productAiDescriptionService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                invoiceItemRepository,
                orderItemRepository,
                productRepository,
                offerRepository,
                receiptSettingsRepository,
                productCategoryOptionService,
                productAiDescriptionService,
                new ObjectMapper()
        );
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
    void shouldDefaultNewProductsToWebsiteAndBillingVisibility() {
        ProductRequest request = new ProductRequest();
        request.setName("Daily Bangles");
        request.setCategory("JEWELLERY");
        request.setSku("JEW-BNG-001");
        request.setCostPrice(BigDecimal.valueOf(70));
        request.setSellingPrice(BigDecimal.valueOf(150));
        request.setQuantity(4);
        request.setLowStockThreshold(2);

        when(productRepository.findBySku("JEW-BNG-001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.prePersist();
            return saved;
        });

        var response = productService.createProduct(request);

        assertTrue(response.getShowOnWebsite());
        assertTrue(response.getUseForBilling());
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
        lowStock.setName("Pearl Bangle");
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

    @Test
    void shouldKeepProductsHiddenFromWebsiteOutOfPublicSurfaces() {
        Product visible = new Product();
        visible.setId(UUID.randomUUID());
        visible.setName("Website Necklace");
        visible.setCategory("JEWELLERY");
        visible.setSku("JEW-WEB-001");
        visible.setSellingPrice(BigDecimal.valueOf(900));
        visible.setQuantity(5);
        visible.setLowStockThreshold(2);
        visible.setCreatedAt(LocalDateTime.now());
        visible.prePersist();

        Product localOnly = new Product();
        localOnly.setId(UUID.randomUUID());
        localOnly.setName("Billing Only Bangle");
        localOnly.setCategory("JEWELLERY");
        localOnly.setSku("JEW-LOCAL-001");
        localOnly.setSellingPrice(BigDecimal.valueOf(300));
        localOnly.setQuantity(5);
        localOnly.setLowStockThreshold(2);
        localOnly.setShowOnWebsite(false);
        localOnly.setCreatedAt(LocalDateTime.now().minusDays(1));
        localOnly.prePersist();

        when(productRepository.findAll()).thenReturn(List.of(visible, localOnly));
        when(productRepository.findById(localOnly.getId())).thenReturn(Optional.of(localOnly));
        when(orderItemRepository.findTrendingProductSales(any())).thenReturn(List.of(
                new Object[]{localOnly.getId(), 20L},
                new Object[]{visible.getId(), 10L}
        ));

        assertEquals(List.of(visible.getId()), productService.getPublicCatalog().stream().map(product -> product.getId()).toList());
        assertEquals(List.of(visible.getId()), productService.getPublicTrendingProducts(5).stream().map(product -> product.getId()).toList());
        assertThrows(com.retailshop.exception.ResourceNotFoundException.class, () -> productService.getPublicProduct(localOnly.getId()));
    }

    @Test
    void shouldSoftDeleteProductAndHideItFromActiveSurfaces() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("Receipt Linked Necklace");
        product.setCategory("NECKLACE");
        product.setSku("NEC-REC-001");
        product.setCostPrice(BigDecimal.valueOf(500));
        product.setSellingPrice(BigDecimal.valueOf(1000));
        product.setQuantity(4);
        product.setLowStockThreshold(2);
        product.setShowOnWebsite(true);
        product.setUseForBilling(true);
        product.setShowInEditorsPicks(true);
        product.setShowInNewRelease(true);
        product.setShowInCustomerAccess(true);
        product.setShowInShopCollection(true);
        product.setShowInFeaturedPieces(true);
        product.setShowInStory(true);
        product.setShowInCuratedSelections(true);
        product.prePersist();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.deleteProduct(productId);

        assertFalse(product.getActive());
        assertFalse(product.getShowOnWebsite());
        assertFalse(product.getUseForBilling());
        assertFalse(product.getShowInEditorsPicks());
        assertFalse(product.getShowInNewRelease());
        assertFalse(product.getShowInCustomerAccess());
        assertFalse(product.getShowInShopCollection());
        assertFalse(product.getShowInFeaturedPieces());
        assertFalse(product.getShowInStory());
        assertFalse(product.getShowInCuratedSelections());
        verify(productRepository).save(product);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void shouldApplyPercentageOfferMaxDiscountCapOnPublicProductPrice() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Premium Necklace");
        product.setCategory("NECKLACE");
        product.setSku("NECK-CAP-001");
        product.setCostPrice(BigDecimal.valueOf(5000));
        product.setSellingPrice(BigDecimal.valueOf(10000));
        product.setQuantity(5);
        product.setLowStockThreshold(2);
        product.setCreatedAt(LocalDateTime.now());
        product.prePersist();

        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        offer.setName("Necklace Offer");
        offer.setType(OfferType.PERCENT);
        offer.setDiscountType(DiscountType.PERCENT);
        offer.setDiscountValue(BigDecimal.valueOf(20));
        offer.setMaxDiscountAmount(BigDecimal.valueOf(1500));
        offer.setCategory("NECKLACE");
        offer.setActive(true);

        when(productRepository.findAll()).thenReturn(List.of(product));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of(offer));

        var catalog = productService.getPublicCatalog();

        assertEquals(BigDecimal.valueOf(8500.00).setScale(2), catalog.get(0).getOfferPrice());
        assertEquals(BigDecimal.valueOf(1500.00).setScale(2), catalog.get(0).getYouSave());
    }
}
