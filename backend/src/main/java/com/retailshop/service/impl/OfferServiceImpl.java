package com.retailshop.service.impl;

import com.retailshop.dto.OfferRequest;
import com.retailshop.dto.OfferResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.enums.OfferType;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.AutomationService;
import com.retailshop.service.OfferService;
import com.retailshop.service.ProductCategoryOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferServiceImpl implements OfferService {

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final AutomationService automationService;
    private final ProductCategoryOptionService productCategoryOptionService;

    @Override
    @Transactional
    public OfferResponse createOffer(OfferRequest request) {
        Offer offer = new Offer();
        applyRequest(offer, request);
        OfferResponse response = mapToResponse(offerRepository.save(offer));
        automationService.distributeOfferAnnouncement(buildOfferMarketingMessage(response));
        return response;
    }

    @Override
    @Transactional
    public OfferResponse updateOffer(UUID id, OfferRequest request) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        applyRequest(offer, request);
        OfferResponse response = mapToResponse(offerRepository.save(offer));
        if (Boolean.TRUE.equals(response.getActive())) {
            automationService.distributeOfferAnnouncement(buildOfferMarketingMessage(response));
        }
        return response;
    }

    @Override
    @Transactional
    public void deleteOffer(UUID id) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        offerRepository.delete(offer);
    }

    private void applyRequest(Offer offer, OfferRequest request) {
        validateRequest(request);
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("Offer end date cannot be before start date");
        }
        offer.setName(request.getName());
        offer.setType(request.getType());
        offer.setValue(request.getValue());
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            productCategoryOptionService.validateCategoryCode(request.getCategory());
            offer.setCategory(request.getCategory().trim().toUpperCase(java.util.Locale.ROOT));
        } else {
            offer.setCategory(null);
        }
        offer.setStartDate(request.getStartDate());
        offer.setEndDate(request.getEndDate());
        offer.setActive(request.getActive());
        offer.setCouponCode(request.getCouponCode() == null || request.getCouponCode().isBlank()
                ? null
                : request.getCouponCode().trim().toUpperCase(java.util.Locale.ROOT));
        offer.setDiscountType(request.getDiscountType());
        offer.setDiscountValue(request.getDiscountValue());
        offer.setMaxDiscountAmount(request.getMaxDiscountAmount());
        offer.setMinOrderValue(request.getMinOrderValue());
        offer.setApplicableOn(request.getApplicableOn());
        offer.setValidFrom(request.getValidFrom());
        offer.setValidTo(request.getValidTo());
        offer.setProduct(null);
        if (request.getProductId() != null) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            offer.setProduct(product);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<OfferResponse> getActiveOffers(Pageable pageable) {
        return PaginatedResponse.from(
                offerRepository.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        LocalDate.now(),
                        LocalDate.now(),
                        pageable
                ).map(this::mapToResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateBestDiscount(Product product, int quantity) {
        BigDecimal lineTotal = product.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
        return offerRepository.findActiveOffers(LocalDate.now())
                .stream()
                .filter(offer -> isApplicable(offer, product))
                .map(offer -> evaluateDiscount(offer, lineTotal))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public String buildOfferMarketingMessage(OfferResponse offer) {
        String target = offer.getProductName() != null
                ? "on " + offer.getProductName()
                : offer.getCategory() != null ? "on " + offer.getCategory() + " collection" : "storewide";
        return "New offer: " + offer.getName() + " is now live " + target + ". Visit us before "
                + offer.getEndDate() + " to enjoy the deal.";
    }

    private boolean isApplicable(Offer offer, Product product) {
        if (offer.getProduct() != null) {
            return offer.getProduct().getId().equals(product.getId());
        }
        if (offer.getCategory() != null) {
            return offer.getCategory().equalsIgnoreCase(product.getCategory());
        }
        return offer.getType() == OfferType.FLAT || offer.getType() == OfferType.PERCENT;
    }

    private BigDecimal evaluateDiscount(Offer offer, BigDecimal lineTotal) {
        BigDecimal discount = switch (offer.getType()) {
            case FLAT -> offer.getValue().min(lineTotal);
            case PERCENT, CATEGORY -> lineTotal.multiply(offer.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        };
        if ((offer.getType() == OfferType.PERCENT || offer.getType() == OfferType.CATEGORY)
                && offer.getMaxDiscountAmount() != null) {
            discount = discount.min(offer.getMaxDiscountAmount());
        }
        return discount;
    }

    private void validateRequest(OfferRequest request) {
        if ((request.getType() == OfferType.PERCENT || request.getType() == OfferType.CATEGORY)
                && request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Percent-based offers cannot exceed 100%");
        }
        if (request.getType() == OfferType.CATEGORY && request.getCategory() == null) {
            throw new BusinessException("Category offer must target a category");
        }
        if (request.getProductId() != null && request.getCategory() != null) {
            throw new BusinessException("Offer can target either a product or a category, not both");
        }
        if (request.getType() == OfferType.CATEGORY && request.getProductId() != null) {
            throw new BusinessException("Category offers cannot target a specific product");
        }
        if (request.getType() != OfferType.CATEGORY && request.getProductId() == null && request.getCategory() == null) {
            throw new BusinessException("Offer must target a product or category");
        }
        if (request.getDiscountType() == DiscountType.PERCENT
                && request.getDiscountValue() != null
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Coupon percentage cannot exceed 100%");
        }
    }

    private OfferResponse mapToResponse(Offer offer) {
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
}
