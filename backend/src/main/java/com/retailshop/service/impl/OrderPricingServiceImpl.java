package com.retailshop.service.impl;

import com.retailshop.config.AppProperties;
import com.retailshop.dto.CartResponse;
import com.retailshop.dto.OfferResponse;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CartService;
import com.retailshop.service.pricing.OrderPricingItem;
import com.retailshop.service.pricing.OrderPricingResult;
import com.retailshop.service.pricing.OrderPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderPricingServiceImpl implements OrderPricingService {

    private final AppProperties appProperties;
    private final CartService cartService;
    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderPricingResult priceCart(UUID customerId, String couponCode) {
        CartResponse cart = cartService.getCart(customerId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return emptyResult(null);
        }

        Map<UUID, Integer> quantities = cart.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProductId(),
                        item -> item.getQuantity(),
                        Integer::sum,
                        LinkedHashMap::new
                ));
        return priceProducts(quantities, couponCode, PriceMode.WEBSITE);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPricingResult priceProducts(Map<UUID, Integer> normalizedItems, String couponCode) {
        return priceProducts(normalizedItems, couponCode, PriceMode.SHOP);
    }

    private OrderPricingResult priceProducts(Map<UUID, Integer> normalizedItems, String couponCode, PriceMode priceMode) {
        if (normalizedItems == null || normalizedItems.isEmpty()) {
            return emptyResult(normalizeCouponCode(couponCode));
        }

        Map<UUID, Product> productsById = productRepository.findAllById(normalizedItems.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        if (productsById.size() != normalizedItems.size()) {
            throw new ResourceNotFoundException("Product not found");
        }

        LocalDate today = LocalDate.now();
        List<Offer> activeOffers = offerRepository.findActiveOffers(today);
        List<OrderPricingItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal automaticDiscount = BigDecimal.ZERO;

        for (Map.Entry<UUID, Integer> entry : normalizedItems.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int quantity = Math.max(0, entry.getValue());
            if (product == null || quantity <= 0) {
                continue;
            }

            BigDecimal unitPrice = priceFor(product, priceMode);
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            DiscountMatch automaticMatch = bestAutomaticDiscount(product, lineTotal, activeOffers);

            subtotal = subtotal.add(lineTotal);
            automaticDiscount = automaticDiscount.add(automaticMatch.amount());
            items.add(OrderPricingItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .sku(product.getSku())
                    .category(product.getCategory())
                    .imageDataUrl(product.getImageDataUrl())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .stockAvailable(product.getQuantity())
                    .lineTotal(lineTotal)
                    .automaticDiscount(automaticMatch.amount())
                    .appliedOfferId(automaticMatch.offer() != null ? automaticMatch.offer().getId() : null)
                    .appliedOfferName(automaticMatch.offer() != null ? automaticMatch.offer().getName() : null)
                    .build());
        }

        String normalizedCouponCode = normalizeCouponCode(couponCode);
        CouponEvaluation couponEvaluation = evaluateCoupon(items, subtotal, normalizedCouponCode, today);
        BigDecimal selectedDiscount = normalizedCouponCode == null
                ? automaticDiscount
                : couponEvaluation.discount();
        BigDecimal finalSubtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAutomaticDiscount = automaticDiscount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalSelectedDiscount = selectedDiscount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = calculateTax(finalSubtotal.subtract(finalSelectedDiscount));
        BigDecimal delivery = calculateDelivery(finalSubtotal.subtract(finalSelectedDiscount));
        BigDecimal finalTotal = finalSubtotal
                .subtract(finalSelectedDiscount)
                .add(tax)
                .add(delivery)
                .setScale(2, RoundingMode.HALF_UP);

        return OrderPricingResult.builder()
                .items(items)
                .applicableOffers(activeOffers.stream()
                        .filter(offer -> isOfferRelevant(offer, items, finalSubtotal, today))
                        .map(this::mapOffer)
                        .toList())
                .requestedCouponCode(normalizedCouponCode)
                .appliedCouponCode(normalizedCouponCode == null ? null : couponEvaluation.appliedCouponCode())
                .subtotal(finalSubtotal)
                .automaticDiscount(finalAutomaticDiscount)
                .couponDiscount(couponEvaluation.discount())
                .discount(finalSelectedDiscount)
                .tax(tax)
                .delivery(delivery)
                .finalTotal(finalTotal)
                .build();
    }

    private OrderPricingResult emptyResult(String requestedCouponCode) {
        return OrderPricingResult.builder()
                .items(List.of())
                .applicableOffers(List.of())
                .requestedCouponCode(requestedCouponCode)
                .appliedCouponCode(null)
                .subtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .automaticDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .couponDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .discount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .tax(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .delivery(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .finalTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private DiscountMatch bestAutomaticDiscount(Product product, BigDecimal lineTotal, Collection<Offer> offers) {
        return offers.stream()
                .filter(offer -> offer.getCouponCode() == null || offer.getCouponCode().isBlank())
                .filter(offer -> appliesTo(offer, product))
                .map(offer -> new DiscountMatch(offer, discountFor(offer, lineTotal)))
                .max(Comparator.comparing(DiscountMatch::amount)
                        .thenComparing(match -> match.offer() != null ? match.offer().getName() : "", String.CASE_INSENSITIVE_ORDER))
                .orElse(new DiscountMatch(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
    }

    private CouponEvaluation evaluateCoupon(List<OrderPricingItem> items,
                                            BigDecimal subtotal,
                                            String couponCode,
                                            LocalDate today) {
        if (couponCode == null) {
            return new CouponEvaluation(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        Offer coupon = offerRepository.findActiveCoupon(couponCode, today).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Invalid coupon"));

        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new BusinessException("Coupon requires minimum order value of " + coupon.getMinOrderValue());
        }

        Map<UUID, Product> productsById = productRepository.findAllById(
                        items.stream().map(OrderPricingItem::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        BigDecimal eligibleTotal = items.stream()
                .filter(item -> appliesTo(coupon, productsById.get(item.getProductId())))
                .map(OrderPricingItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (eligibleTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Coupon is not applicable to cart items");
        }

        return new CouponEvaluation(couponCode, discountFor(coupon, eligibleTotal));
    }

    private boolean isOfferRelevant(Offer offer,
                                    List<OrderPricingItem> items,
                                    BigDecimal subtotal,
                                    LocalDate today) {
        if (!Boolean.TRUE.equals(offer.getActive())) {
            return false;
        }
        if (offer.getStartDate() != null && offer.getStartDate().isAfter(today)) {
            return false;
        }
        if (offer.getEndDate() != null && offer.getEndDate().isBefore(today)) {
            return false;
        }
        if (offer.getCouponCode() != null
                && !offer.getCouponCode().isBlank()
                && offer.getMinOrderValue() != null
                && subtotal.compareTo(offer.getMinOrderValue()) < 0) {
            return false;
        }
        return items.stream().anyMatch(item -> {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            return appliesTo(offer, product);
        });
    }

    private boolean appliesTo(Offer offer, Product product) {
        if (offer == null || product == null) {
            return false;
        }
        if (offer.getProduct() != null) {
            return offer.getProduct().getId().equals(product.getId());
        }
        if (offer.getCategory() != null && !offer.getCategory().isBlank()) {
            return offer.getCategory().equalsIgnoreCase(product.getCategory());
        }
        return true;
    }

    private BigDecimal discountFor(Offer offer, BigDecimal base) {
        DiscountType discountType = offer.getDiscountType();
        BigDecimal value = offer.getDiscountValue();
        if (discountType == null || value == null) {
            discountType = switch (offer.getType()) {
                case FLAT -> DiscountType.FLAT;
                case PERCENT, CATEGORY -> DiscountType.PERCENT;
            };
            value = offer.getValue();
        }

        BigDecimal discount = discountType == DiscountType.PERCENT
                ? base.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;
        if (offer.getMaxDiscountAmount() != null && discountType == DiscountType.PERCENT) {
            discount = discount.min(offer.getMaxDiscountAmount());
        }
        return discount.min(base).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTax(BigDecimal taxableAmount) {
        BigDecimal taxPercent = appProperties.getPricing() == null
                ? BigDecimal.ZERO
                : appProperties.getPricing().getTaxPercent();
        if (taxPercent == null || taxPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return taxableAmount.max(BigDecimal.ZERO)
                .multiply(taxPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDelivery(BigDecimal discountedSubtotal) {
        BigDecimal deliveryCharge = appProperties.getPricing() == null
                ? BigDecimal.ZERO
                : appProperties.getPricing().getDeliveryCharge();
        BigDecimal freeDeliveryMinOrder = appProperties.getPricing() == null
                ? BigDecimal.ZERO
                : appProperties.getPricing().getFreeDeliveryMinOrder();
        if (deliveryCharge == null || deliveryCharge.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (freeDeliveryMinOrder != null && discountedSubtotal.compareTo(freeDeliveryMinOrder) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return deliveryCharge.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCouponCode(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        return couponCode.trim().toUpperCase(Locale.ROOT);
    }

    private OfferResponse mapOffer(Offer offer) {
        return OfferResponse.builder()
                .id(offer.getId())
                .name(offer.getName())
                .type(offer.getType())
                .value(offer.getValue())
                .category(offer.getCategory())
                .productId(offer.getProduct() != null ? offer.getProduct().getId() : null)
                .productName(offer.getProduct() != null ? offer.getProduct().getName() : null)
                .startDate(offer.getStartDate())
                .endDate(offer.getEndDate())
                .active(offer.getActive())
                .couponCode(offer.getCouponCode())
                .discountType(offer.getDiscountType())
                .discountValue(offer.getDiscountValue())
                .maxDiscountAmount(offer.getMaxDiscountAmount())
                .minOrderValue(offer.getMinOrderValue())
                .applicableOn(offer.getApplicableOn())
                .validFrom(offer.getValidFrom())
                .validTo(offer.getValidTo())
                .build();
    }

    private BigDecimal priceFor(Product product, PriceMode priceMode) {
        BigDecimal sourcePrice = priceMode == PriceMode.WEBSITE
                ? product.getResolvedWebsitePrice()
                : product.getSellingPrice();
        return sourcePrice == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : sourcePrice.setScale(2, RoundingMode.HALF_UP);
    }

    private record DiscountMatch(Offer offer, BigDecimal amount) {
    }

    private record CouponEvaluation(String appliedCouponCode, BigDecimal discount) {
    }

    private enum PriceMode {
        SHOP,
        WEBSITE
    }
}
