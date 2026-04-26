package com.retailshop.service.impl;

import com.retailshop.dto.OfferRequest;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.OfferType;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.AutomationService;
import com.retailshop.service.ProductCategoryOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceImplTest {

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AutomationService automationService;

    @Mock
    private ProductCategoryOptionService productCategoryOptionService;

    @InjectMocks
    private OfferServiceImpl offerService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Rose Matte Lipstick");
        product.setCategory("COSMETICS");
        product.setSellingPrice(BigDecimal.valueOf(299));
    }

    @Test
    void shouldRejectPercentOfferAboveHundred() {
        OfferRequest request = new OfferRequest();
        request.setName("Too much");
        request.setType(OfferType.PERCENT);
        request.setValue(BigDecimal.valueOf(120));
        request.setCategory("COSMETICS");
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(2));
        request.setActive(true);

        assertThrows(BusinessException.class, () -> offerService.createOffer(request));
        verify(offerRepository, never()).save(any());
    }

    @Test
    void shouldRejectCategoryOfferWithoutCategory() {
        OfferRequest request = new OfferRequest();
        request.setName("Missing category");
        request.setType(OfferType.CATEGORY);
        request.setValue(BigDecimal.valueOf(10));
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(2));
        request.setActive(true);

        assertThrows(BusinessException.class, () -> offerService.createOffer(request));
    }

    @Test
    void shouldCreateProductOfferAndTriggerAutomation() {
        OfferRequest request = new OfferRequest();
        request.setName("Lipstick 15%");
        request.setType(OfferType.PERCENT);
        request.setValue(BigDecimal.valueOf(15));
        request.setProductId(product.getId());
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(5));
        request.setActive(true);

        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer offer = invocation.getArgument(0);
            offer.setId(UUID.randomUUID());
            return offer;
        });

        var response = offerService.createOffer(request);

        assertEquals(product.getId(), response.getProductId());
        verify(automationService).distributeOfferAnnouncement(any());
    }

    @Test
    void shouldApplyBestAvailableOfferWithoutStacking() {
        Offer productOffer = new Offer();
        productOffer.setId(UUID.randomUUID());
        productOffer.setType(OfferType.FLAT);
        productOffer.setValue(BigDecimal.valueOf(40));
        productOffer.setProduct(product);
        productOffer.setStartDate(LocalDate.now().minusDays(1));
        productOffer.setEndDate(LocalDate.now().plusDays(1));
        productOffer.setActive(true);

        Offer categoryOffer = new Offer();
        categoryOffer.setId(UUID.randomUUID());
        categoryOffer.setType(OfferType.CATEGORY);
        categoryOffer.setValue(BigDecimal.valueOf(20));
        categoryOffer.setCategory("COSMETICS");
        categoryOffer.setStartDate(LocalDate.now().minusDays(1));
        categoryOffer.setEndDate(LocalDate.now().plusDays(1));
        categoryOffer.setActive(true);

        when(offerRepository.findActiveOffers(LocalDate.now())).thenReturn(List.of(productOffer, categoryOffer));

        BigDecimal discount = offerService.calculateBestDiscount(product, 2);

        assertEquals(BigDecimal.valueOf(119.60).setScale(2), discount);
    }
}
