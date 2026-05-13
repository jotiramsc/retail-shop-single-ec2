package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.OfferSuggestionResponse;
import com.retailshop.entity.Campaign;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderStatus;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.OrderItemRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.AutomationService;
import com.retailshop.service.SocialMediaService;
import com.retailshop.service.WhatsAppMessageService;
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
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final WhatsAppMessageService whatsAppMessageService;
    private final SocialMediaService socialMediaService;

    @Override
    @Transactional(readOnly = true)
    public List<OfferSuggestionResponse> suggestOffersForSlowMovingProducts() {
        Map<UUID, Long> soldByProduct = new HashMap<>();
        for (Object[] row : invoiceItemRepository.aggregateQuantitySoldByProduct()) {
            soldByProduct.put((UUID) row[0], (Long) row[1]);
        }
        Map<UUID, Long> onlineDemandByProduct = new HashMap<>();
        for (Object[] row : orderItemRepository.findTrendingProductSales(OrderStatus.CANCELLED)) {
            onlineDemandByProduct.put((UUID) row[0], (Long) row[1]);
        }

        return productRepository.findAll()
                .stream()
                .filter(product -> product.getQuantity() >= appProperties.getSlowMovingMinStock())
                .filter(product -> shouldSuggestOffer(product, soldByProduct, onlineDemandByProduct))
                .sorted((left, right) -> Double.compare(
                        suggestionScore(right, soldByProduct, onlineDemandByProduct),
                        suggestionScore(left, soldByProduct, onlineDemandByProduct)
                ))
                .limit(25)
                .map(product -> buildSuggestion(product, soldByProduct, onlineDemandByProduct))
                .toList();
    }

    @Override
    public void distributeOfferAnnouncement(String message) {
        whatsAppMessageService.broadcastOffer(customerRepository.findAll(), message);
        Campaign campaign = new Campaign();
        campaign.setName("Automated offer announcement");
        campaign.setContent(message);
        socialMediaService.publishInstagram(campaign);
        socialMediaService.publishFacebook(campaign);
    }

    private boolean shouldSuggestOffer(Product product,
                                       Map<UUID, Long> soldByProduct,
                                       Map<UUID, Long> onlineDemandByProduct) {
        long offlineSold = soldByProduct.getOrDefault(product.getId(), 0L);
        long onlineDemand = onlineDemandByProduct.getOrDefault(product.getId(), 0L);
        boolean slowOfflineSales = offlineSold < appProperties.getSlowMovingMaxUnitsSold();
        boolean stockPressure = product.getQuantity() >= Math.max(appProperties.getSlowMovingMinStock(), product.getLowStockThreshold() * 3);
        boolean demandReadyStock = onlineDemand >= appProperties.getSlowMovingMaxUnitsSold() && stockPressure;
        return slowOfflineSales || demandReadyStock;
    }

    private double suggestionScore(Product product,
                                   Map<UUID, Long> soldByProduct,
                                   Map<UUID, Long> onlineDemandByProduct) {
        long offlineSold = soldByProduct.getOrDefault(product.getId(), 0L);
        long onlineDemand = onlineDemandByProduct.getOrDefault(product.getId(), 0L);
        double stockPressure = Math.max(0, product.getQuantity() - product.getLowStockThreshold());
        double slowSalesBoost = Math.max(0, appProperties.getSlowMovingMaxUnitsSold() - offlineSold) * 12.0;
        return stockPressure + slowSalesBoost + (onlineDemand * 4.0);
    }

    private OfferSuggestionResponse buildSuggestion(Product product,
                                                    Map<UUID, Long> soldByProduct,
                                                    Map<UUID, Long> onlineDemandByProduct) {
        long offlineSold = soldByProduct.getOrDefault(product.getId(), 0L);
        long onlineDemand = onlineDemandByProduct.getOrDefault(product.getId(), 0L);
        BigDecimal base = "JEWELLERY".equalsIgnoreCase(product.getCategory())
                ? BigDecimal.valueOf(8)
                : BigDecimal.valueOf(12);
        BigDecimal stockBoost = product.getQuantity() >= appProperties.getSlowMovingMinStock() * 3
                ? BigDecimal.valueOf(4)
                : BigDecimal.ZERO;
        BigDecimal demandBoost = onlineDemand > offlineSold
                ? BigDecimal.valueOf(2)
                : BigDecimal.ZERO;
        BigDecimal suggested = base.add(stockBoost).add(demandBoost).min(BigDecimal.valueOf(25));
        return OfferSuggestionResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .currentQuantity(product.getQuantity())
                .suggestedDiscountPercent(suggested)
                .reason("Stock " + product.getQuantity()
                        + ", invoice sales " + offlineSold
                        + ", online paid demand " + onlineDemand
                        + " - suggested to balance demand, stock, and sales velocity")
                .build();
    }
}
