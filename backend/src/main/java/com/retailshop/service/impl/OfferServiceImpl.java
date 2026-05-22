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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return createOffer(request, true);
    }

    @Override
    @Transactional
    public OfferResponse createOfferSilently(OfferRequest request) {
        return createOffer(request, false);
    }

    private OfferResponse createOffer(OfferRequest request, boolean announce) {
        Offer offer = new Offer();
        applyRequest(offer, request);
        OfferResponse response = mapToResponse(offerRepository.save(offer));
        if (announce) {
            automationService.distributeOfferAnnouncement(buildOfferMarketingMessage(response));
        }
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
        normalizeRequestDefaults(offer, request);
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
        offer.setCouponCode(request.getCouponCode());
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
        offer.setBuyCategory(normalizeCategory(request.getBuyCategory()));
        offer.setGetCategory(normalizeCategory(request.getGetCategory()));
        offer.setBuyQuantity(request.getBuyQuantity());
        offer.setGetQuantity(request.getGetQuantity());
        offer.setRewardMode(normalizeOptionalUpper(request.getRewardMode()));
        offer.setRewardDiscountPercent(request.getRewardDiscountPercent());
        offer.setScheduleType(normalizeScheduleType(request.getScheduleType()));
        offer.setSpecificDays(normalizeSpecificDays(request.getSpecificDays(), offer.getScheduleType()));
        offer.setBuyProduct(null);
        if (request.getBuyProductId() != null) {
            Product buyProduct = productRepository.findById(request.getBuyProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Buy product not found"));
            offer.setBuyProduct(buyProduct);
        }
        offer.setGetProduct(null);
        if (request.getGetProductId() != null) {
            Product getProduct = productRepository.findById(request.getGetProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Get product not found"));
            offer.setGetProduct(getProduct);
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
                .map(offer -> evaluateDiscount(offer, product, quantity, lineTotal))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public String buildOfferMarketingMessage(OfferResponse offer) {
        String target = offer.getProductName() != null
                ? "on " + offer.getProductName()
                : offer.getCategory() != null ? "on " + offer.getCategory() + " collection" : "storewide";
        if (offer.getType() == OfferType.BUY_ONE_GET_ONE || offer.getType() == OfferType.BUY_X_GET_Y) {
            target = offer.getBuyProductName() != null
                    ? "on " + offer.getBuyProductName()
                    : offer.getBuyCategory() != null ? "on " + offer.getBuyCategory() + " collection" : target;
        }
        return "New offer: " + offer.getName() + " is now live " + target + ". Visit us before "
                + offer.getEndDate() + " to enjoy the deal.";
    }

    private boolean isApplicable(Offer offer, Product product) {
        if (offer.getType() == OfferType.BUY_ONE_GET_ONE || offer.getType() == OfferType.BUY_X_GET_Y) {
            return isBuyTargetApplicable(offer, product);
        }
        if (offer.getType() == OfferType.COMBO) {
            return false;
        }
        if (offer.getProduct() != null) {
            return offer.getProduct().getId().equals(product.getId());
        }
        if (offer.getCategory() != null) {
            return offer.getCategory().equalsIgnoreCase(product.getCategory());
        }
        return offer.getType() == OfferType.FLAT || offer.getType() == OfferType.PERCENT;
    }

    private BigDecimal evaluateDiscount(Offer offer, Product product, int quantity, BigDecimal lineTotal) {
        BigDecimal discount = switch (offer.getType()) {
            case FLAT -> offer.getValue().min(lineTotal);
            case PERCENT, CATEGORY -> lineTotal.multiply(offer.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case BUY_ONE_GET_ONE, BUY_X_GET_Y -> evaluateBuyGetDiscount(offer, product, quantity);
            case COMBO -> BigDecimal.ZERO;
        };
        if ((offer.getType() == OfferType.PERCENT || offer.getType() == OfferType.CATEGORY)
                && offer.getMaxDiscountAmount() != null) {
            discount = discount.min(offer.getMaxDiscountAmount());
        }
        return discount;
    }

    private BigDecimal evaluateBuyGetDiscount(Offer offer, Product product, int quantity) {
        if (!isBuyTargetApplicable(offer, product)) {
            return BigDecimal.ZERO;
        }
        boolean sameRewardProduct = offer.getGetProduct() == null
                || offer.getGetProduct().getId().equals(product.getId());
        boolean sameRewardCategory = offer.getGetCategory() == null
                || offer.getGetCategory().equalsIgnoreCase(product.getCategory());
        if (!sameRewardProduct || !sameRewardCategory) {
            return BigDecimal.ZERO;
        }
        int buyQty = positiveOrDefault(offer.getBuyQuantity(), 1);
        int getQty = positiveOrDefault(offer.getGetQuantity(), 1);
        int bundleSize = buyQty + getQty;
        if (quantity < bundleSize) {
            return BigDecimal.ZERO;
        }
        int rewardedUnits = (quantity / bundleSize) * getQty;
        BigDecimal percent = offer.getRewardDiscountPercent() == null
                ? BigDecimal.valueOf(100)
                : offer.getRewardDiscountPercent();
        return product.getSellingPrice()
                .multiply(BigDecimal.valueOf(rewardedUnits))
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validateRequest(OfferRequest request) {
        String scheduleType = normalizeScheduleType(request.getScheduleType());
        if ("SPECIFIC_DAYS".equals(scheduleType) && normalizeSpecificDays(request.getSpecificDays(), scheduleType) == null) {
            throw new BusinessException("Please select at least one day for this offer.");
        }
        if (request.getType() == OfferType.BUY_ONE_GET_ONE
                || request.getType() == OfferType.BUY_X_GET_Y
                || request.getType() == OfferType.COMBO) {
            validateBuyGetRequest(request);
            return;
        }
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
        if (request.getType() != OfferType.CATEGORY
                && request.getProductId() == null
                && request.getCategory() == null
                && !"STORE".equalsIgnoreCase(request.getApplicableOn())) {
            throw new BusinessException("Offer must target a product or category");
        }
        if (request.getDiscountType() == DiscountType.PERCENT
                && request.getDiscountValue() != null
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Coupon percentage cannot exceed 100%");
        }
    }

    private void validateBuyGetRequest(OfferRequest request) {
        if (request.getBuyProductId() == null && isBlank(request.getBuyCategory())) {
            throw new BusinessException("Buy/Get offers need a buy product or category");
        }
        if (request.getGetProductId() == null && isBlank(request.getGetCategory())) {
            throw new BusinessException("Buy/Get offers need a reward product or category");
        }
        if (positiveOrDefault(request.getBuyQuantity(), 0) <= 0) {
            throw new BusinessException("Buy quantity must be greater than zero");
        }
        if (positiveOrDefault(request.getGetQuantity(), 0) <= 0) {
            throw new BusinessException("Reward quantity must be greater than zero");
        }
        if (request.getRewardDiscountPercent() != null
                && request.getRewardDiscountPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Reward discount percentage cannot exceed 100%");
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
                .buyCategory(offer.getBuyCategory())
                .buyProductId(offer.getBuyProduct() != null ? offer.getBuyProduct().getId() : null)
                .buyProductName(offer.getBuyProduct() != null ? offer.getBuyProduct().getName() : null)
                .buyQuantity(offer.getBuyQuantity())
                .getCategory(offer.getGetCategory())
                .getProductId(offer.getGetProduct() != null ? offer.getGetProduct().getId() : null)
                .getProductName(offer.getGetProduct() != null ? offer.getGetProduct().getName() : null)
                .getQuantity(offer.getGetQuantity())
                .rewardMode(offer.getRewardMode())
                .rewardDiscountPercent(offer.getRewardDiscountPercent())
                .scheduleType(normalizeScheduleType(offer.getScheduleType()))
                .specificDays(splitSpecificDays(offer.getSpecificDays()))
                .build();
    }

    private boolean isBuyTargetApplicable(Offer offer, Product product) {
        if (offer.getBuyProduct() != null) {
            return offer.getBuyProduct().getId().equals(product.getId());
        }
        return offer.getBuyCategory() != null && offer.getBuyCategory().equalsIgnoreCase(product.getCategory());
    }

    private String normalizeCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        productCategoryOptionService.validateCategoryCode(category);
        return category.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeOptionalUpper(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void normalizeRequestDefaults(Offer offer, OfferRequest request) {
        String scheduleType = normalizeScheduleType(request.getScheduleType());
        request.setScheduleType(scheduleType);
        if ("WEEKEND_ONLY".equals(scheduleType)) {
            request.setSpecificDays(List.of("SAT", "SUN"));
        }
        LocalDate today = LocalDate.now();
        if (request.getStartDate() == null) {
            request.setStartDate(today);
        }
        if (request.getEndDate() == null) {
            request.setEndDate("ALWAYS_ACTIVE".equals(scheduleType)
                    ? request.getStartDate().plusYears(20)
                    : request.getStartDate().plusMonths(1));
        }
        if (request.getActive() == null) {
            request.setActive(Boolean.TRUE);
        }
        if (request.getType() == null) {
            request.setType(OfferType.PERCENT);
        }
        if (request.getValue() == null) {
            request.setValue(BigDecimal.ZERO);
        }
        String couponCode = normalizeCouponCode(request.getCouponCode());
        request.setCouponCode(couponCode == null
                ? generateUniqueCouponCode(request.getName(), offer.getId())
                : uniqueCouponCode(couponCode, offer.getId()));
    }

    private String normalizeCouponCode(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isBlank() ? null : normalized.substring(0, Math.min(6, normalized.length()));
    }

    private String generateUniqueCouponCode(String offerName, UUID currentOfferId) {
        String base = normalizeCouponCode(offerName);
        return uniqueCouponCode(base == null ? "OFFER" : base, currentOfferId);
    }

    private String uniqueCouponCode(String baseCode, UUID currentOfferId) {
        String base = normalizeCouponCode(baseCode);
        if (base == null) {
            base = "OFFER";
        }
        if (isCouponAvailable(base, currentOfferId)) {
            return base;
        }
        String stem = base.substring(0, Math.min(5, base.length()));
        for (int index = 1; index <= 9; index++) {
            String candidate = stem + index;
            if (isCouponAvailable(candidate, currentOfferId)) {
                return candidate;
            }
        }
        return stem + "9";
    }

    private boolean isCouponAvailable(String couponCode, UUID currentOfferId) {
        List<Offer> matches = offerRepository.findByCouponCodeIgnoreCase(couponCode);
        if (matches == null) {
            return true;
        }
        return matches.stream()
                .noneMatch(offer -> currentOfferId == null || !offer.getId().equals(currentOfferId));
    }

    private String normalizeScheduleType(String value) {
        if (isBlank(value)) {
            return "DATE_RANGE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ALWAYS".equals(normalized)) {
            return "ALWAYS_ACTIVE";
        }
        if ("WEEKEND".equals(normalized)) {
            return "WEEKEND_ONLY";
        }
        return switch (normalized) {
            case "ALWAYS_ACTIVE", "DATE_RANGE", "WEEKEND_ONLY", "SPECIFIC_DAYS" -> normalized;
            default -> throw new BusinessException("Choose a valid offer schedule");
        };
    }

    private String normalizeSpecificDays(List<String> days, String scheduleType) {
        if ("WEEKEND_ONLY".equals(scheduleType)) {
            return "SAT,SUN";
        }
        if (!"SPECIFIC_DAYS".equals(scheduleType)) {
            return null;
        }
        if (days == null || days.isEmpty()) {
            return null;
        }
        Set<String> allowed = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        LinkedHashSet<String> selected = days.stream()
                .filter(day -> !isBlank(day))
                .map(day -> day.trim().toUpperCase(Locale.ROOT))
                .map(day -> day.length() > 3 ? day.substring(0, 3) : day)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!allowed.containsAll(selected)) {
            throw new BusinessException("Choose valid weekdays for this offer");
        }
        return selected.isEmpty() ? null : String.join(",", selected);
    }

    private List<String> splitSpecificDays(String days) {
        if (isBlank(days)) {
            return List.of();
        }
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(day -> !day.isBlank())
                .toList();
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
