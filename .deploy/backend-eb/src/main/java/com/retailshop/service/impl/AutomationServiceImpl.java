package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.OfferSuggestionResponse;
import com.retailshop.entity.Product;
import com.retailshop.enums.ProductCategory;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.AutomationService;
import com.retailshop.service.SocialMediaService;
import com.retailshop.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutomationServiceImpl implements AutomationService {

    private final AppProperties appProperties;
    private final ProductRepository productRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final WhatsAppService whatsAppService;
    private final SocialMediaService socialMediaService;

    @Override
    @Transactional(readOnly = true)
    public List<OfferSuggestionResponse> suggestOffersForSlowMovingProducts() {
        Map<UUID, Long> soldByProduct = new HashMap<>();
        for (Object[] row : invoiceItemRepository.aggregateQuantitySoldByProduct()) {
            soldByProduct.put((UUID) row[0], (Long) row[1]);
        }

        return productRepository.findAll()
                .stream()
                .filter(product -> product.getQuantity() >= appProperties.getSlowMovingMinStock())
                .filter(product -> soldByProduct.getOrDefault(product.getId(), 0L) < appProperties.getSlowMovingMaxUnitsSold())
                .map(this::buildSuggestion)
                .toList();
    }

    @Override
    public void distributeOfferAnnouncement(String message) {
        whatsAppService.broadcast(message);
        socialMediaService.postToInstagram(message);
        socialMediaService.postToFacebook(message);
    }

    private OfferSuggestionResponse buildSuggestion(Product product) {
        BigDecimal suggested = product.getCategory() == ProductCategory.JEWELLERY
                ? BigDecimal.valueOf(8)
                : BigDecimal.valueOf(12);
        return OfferSuggestionResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .currentQuantity(product.getQuantity())
                .suggestedDiscountPercent(suggested)
                .reason("High stock with low sales velocity in recent invoices")
                .build();
    }
}
