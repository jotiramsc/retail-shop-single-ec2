package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.CartItemResponse;
import com.retailshop.dto.CartResponse;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.enums.OfferType;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPricingServiceImplTest {

    @Mock
    private CartService cartService;

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private ProductRepository productRepository;

    private AppProperties appProperties;

    @InjectMocks
    private OrderPricingServiceImpl orderPricingService;

    private Product product;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        orderPricingService = new OrderPricingServiceImpl(appProperties, cartService, offerRepository, productRepository);

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Pearl Drop Earrings");
        product.setSku("PEARL-01");
        product.setCategory("JEWELLERY");
        product.setSellingPrice(BigDecimal.valueOf(1000));
        product.setWebsitePricePercentage(BigDecimal.TEN);
        product.setQuantity(10);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        lenient().when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(offerRepository.findActiveOffers(any())).thenReturn(List.of());
    }

    @Test
    void shouldUseSelectedCouponEvenWhenAutomaticOfferIsHigher() {
        Offer automaticOffer = buildOffer("Padwa", null, DiscountType.PERCENT, BigDecimal.valueOf(20));
        Offer couponOffer = buildOffer("Save 10", "SAVE10", DiscountType.FLAT, BigDecimal.valueOf(100));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of(automaticOffer, couponOffer));
        when(offerRepository.findActiveCoupon(eq("SAVE10"), any())).thenReturn(List.of(couponOffer));

        var pricing = orderPricingService.priceProducts(Map.of(product.getId(), 1), "SAVE10");

        assertEquals("SAVE10", pricing.getAppliedCouponCode());
        assertEquals(BigDecimal.valueOf(1000.00).setScale(2), pricing.getSubtotal());
        assertEquals(BigDecimal.valueOf(200.00).setScale(2), pricing.getAutomaticDiscount());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), pricing.getCouponDiscount());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), pricing.getDiscount());
        assertEquals(BigDecimal.valueOf(900.00).setScale(2), pricing.getFinalTotal());
    }

    @Test
    void shouldRejectCouponBelowMinimumOrderValue() {
        Offer couponOffer = buildOffer("Festival 10", "FEST10", DiscountType.PERCENT, BigDecimal.valueOf(10));
        couponOffer.setMinOrderValue(BigDecimal.valueOf(1500));
        when(offerRepository.findActiveOffers(any())).thenReturn(List.of(couponOffer));
        when(offerRepository.findActiveCoupon(eq("FEST10"), any())).thenReturn(List.of(couponOffer));

        assertThrows(BusinessException.class, () -> orderPricingService.priceProducts(Map.of(product.getId(), 1), "FEST10"));
    }

    @Test
    void shouldUseWebsitePriceForCustomerCartPricing() {
        when(cartService.getCart(any())).thenReturn(CartResponse.builder()
                .items(List.of(CartItemResponse.builder()
                        .productId(product.getId())
                        .quantity(1)
                        .build()))
                .subtotal(BigDecimal.ZERO)
                .build());

        var pricing = orderPricingService.priceCart(UUID.randomUUID(), null);

        assertEquals(BigDecimal.valueOf(1100.00).setScale(2), pricing.getSubtotal());
        assertEquals(BigDecimal.valueOf(1100.00).setScale(2), pricing.getFinalTotal());
        assertEquals(BigDecimal.valueOf(1100.00).setScale(2), pricing.getItems().get(0).getUnitPrice());
    }

    private Offer buildOffer(String name, String couponCode, DiscountType discountType, BigDecimal discountValue) {
        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        offer.setName(name);
        offer.setType(OfferType.PERCENT);
        offer.setValue(discountValue);
        offer.setDiscountType(discountType);
        offer.setDiscountValue(discountValue);
        offer.setActive(true);
        offer.setStartDate(LocalDate.now().minusDays(1));
        offer.setEndDate(LocalDate.now().plusDays(1));
        offer.setCouponCode(couponCode);
        offer.setProduct(product);
        return offer;
    }
}
