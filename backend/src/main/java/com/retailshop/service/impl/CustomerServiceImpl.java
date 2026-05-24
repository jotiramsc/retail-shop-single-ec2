package com.retailshop.service.impl;

import com.retailshop.dto.CustomerRequest;
import com.retailshop.dto.CustomerActivityTrackRequest;
import com.retailshop.dto.CustomerDetailsResponse;
import com.retailshop.dto.CustomerEngagementUpdateRequest;
import com.retailshop.dto.CustomerLocationUpdateRequest;
import com.retailshop.dto.CustomerResponse;
import com.retailshop.dto.CustomerLookupResponse;
import com.retailshop.dto.CustomerSupportChatResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.PurchaseHistoryResponse;
import com.retailshop.entity.Address;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerActivityHistory;
import com.retailshop.entity.CustomerLocationHistory;
import com.retailshop.entity.CustomerLoginHistory;
import com.retailshop.entity.OmnichannelConversation;
import com.retailshop.entity.OmnichannelConversationMessage;
import com.retailshop.entity.OmnichannelLead;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderStatus;
import com.retailshop.enums.OrderSource;
import com.retailshop.repository.AddressRepository;
import com.retailshop.repository.CustomerActivityHistoryRepository;
import com.retailshop.repository.CustomerLocationHistoryRepository;
import com.retailshop.repository.CustomerLoginHistoryRepository;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.OmnichannelConversationMessageRepository;
import com.retailshop.repository.OmnichannelConversationRepository;
import com.retailshop.repository.OmnichannelLeadRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CustomerService;
import com.retailshop.util.MobileNumberUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final AddressRepository addressRepository;
    private final CustomerLoginHistoryRepository loginHistoryRepository;
    private final CustomerLocationHistoryRepository locationHistoryRepository;
    private final CustomerActivityHistoryRepository activityHistoryRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductRepository productRepository;
    private final OmnichannelLeadRepository omnichannelLeadRepository;
    private final OmnichannelConversationRepository conversationRepository;
    private final OmnichannelConversationMessageRepository messageRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        findCustomerByMobile(request.getMobile()).ifPresent(customer -> {
            throw new BusinessException("Customer with this mobile already exists");
        });
        String normalizedMobile = normalizedLastTen(request.getMobile());
        if (normalizedMobile.isBlank()) {
            throw new BusinessException("Valid mobile number is required");
        }
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setMobile(normalizedMobile);
        customer.setCustomerSource("ADMIN_CREATED");
        markUnverified(customer);
        return mapToResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<CustomerResponse> getAllCustomers(Pageable pageable, String segment) {
        var page = customerRepository.findAllByOrderByCreatedAtDesc(pageable);
        if (segment == null || segment.isBlank()) {
            return PaginatedResponse.from(page.map(this::mapToResponse));
        }
        String normalizedSegment = normalizeLabel(segment);
        List<CustomerResponse> items = page.getContent().stream()
                .map(this::mapToResponse)
                .filter(response -> customerMatchesSegment(response, normalizedSegment))
                .toList();
        return PaginatedResponse.<CustomerResponse>builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(items.size())
                .totalPages(items.isEmpty() ? 0 : 1)
                .hasNext(false)
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 1) {
            return customerRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent()
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        }

        return customerRepository.searchByNameOrMobile(normalizedQuery)
                .stream()
                .limit(8)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDetailsResponse getCustomerDetails(java.util.UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        var orders = customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        var invoices = invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        var loginHistory = loginHistoryRepository.findTop20ByCustomerIdOrderByLoginAtDesc(customer.getId());
        var locationHistory = locationHistoryRepository.findTop10ByCustomerIdOrderByCreatedAtDesc(customer.getId());
        var activityHistory = activityHistoryRepository.findTop30ByCustomerIdOrderByCreatedAtDesc(customer.getId());
        Set<OrderStatus> closedStatuses = Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        BigDecimal totalSpent = invoices.stream()
                .map(invoice -> Optional.ofNullable(invoice.getFinalAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime lastOrderDate = orders.stream()
                .findFirst()
                .map(order -> order.getCreatedAt())
                .orElse(null);

        return CustomerDetailsResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .email(customer.getEmail())
                .gender(customer.getGender())
                .fullAddress(latestAddress(customer.getId()))
                .customerSince(customer.getCreatedAt())
                .dateOfBirth(customer.getDateOfBirth())
                .anniversaryDate(customer.getAnniversaryDate())
                .spouseName(customer.getSpouseName())
                .preferredLanguage(customer.getPreferredLanguage())
                .preferredCategories(customer.getPreferredCategories())
                .preferredProducts(customer.getPreferredProducts())
                .preferredBrands(customer.getPreferredBrands())
                .preferredPriceRange(customer.getPreferredPriceRange())
                .shoppingInterests(customer.getShoppingInterests())
                .customerNotes(customer.getCustomerNotes())
                .customerTags(customer.getCustomerTags())
                .verificationStatus(verificationStatus(customer))
                .customerSource(customer.getCustomerSource())
                .loginEnabled(customer.isLoginEnabled())
                .otpVerifiedAt(customer.getOtpVerifiedAt())
                .birthdayReminderEnabled(customer.isBirthdayReminderEnabled())
                .anniversaryReminderEnabled(customer.isAnniversaryReminderEnabled())
                .lastLoginAt(customer.getLastLoginAt())
                .lastLoginMethod(customer.getLastLoginMethod())
                .lastActiveAt(latestActiveAt(customer, loginHistory, activityHistory, lastOrderDate))
                .lastKnownLocation(firstNonBlank(customer.getLastKnownLocation(), formatLocation(locationHistory.stream().findFirst().orElse(null)), "Location not shared"))
                .lastOrderDate(lastOrderDate)
                .supportChatStatus(resolveSupportStatus(customer))
                .totalOrders(orders.size())
                .pendingOrders(orders.stream().filter(order -> !closedStatuses.contains(order.getStatus())).count())
                .totalSpent(totalSpent)
                .orderHistory(orders.stream()
                        .limit(12)
                        .map(order -> CustomerDetailsResponse.OrderSummary.builder()
                                .id(order.getId())
                                .orderNumber(order.getOrderNumber())
                                .createdAt(order.getCreatedAt())
                                .amount(order.getFinalAmount())
                                .status(order.getStatus() == null ? "UNKNOWN" : order.getStatus().name())
                                .build())
                        .toList())
                .loginHistory(loginHistory.stream().map(this::mapLogin).toList())
                .locationHistory(locationHistory.stream().map(this::mapLocation).toList())
                .activityHistory(activityHistory.stream().map(this::mapActivity).toList())
                .searchHistory(activityHistory.stream()
                        .filter(activity -> "SEARCH".equalsIgnoreCase(activity.getActivityType()) || hasText(activity.getSearchKeyword()))
                        .map(this::mapActivity)
                        .toList())
                .preferenceInsights(buildPreferenceInsights(customer, activityHistory, orders.size(), totalSpent))
                .segments(buildSegments(customer, orders.size(), totalSpent, activityHistory))
                .timeline(buildTimeline(customer, loginHistory, activityHistory, orders.stream()
                        .limit(10)
                        .map(order -> CustomerDetailsResponse.TimelineEvent.builder()
                                .createdAt(order.getCreatedAt())
                                .type("ORDER")
                                .title("Placed order " + order.getOrderNumber())
                                .detail(currencyText(order.getFinalAmount()) + " · " + (order.getStatus() == null ? "UNKNOWN" : order.getStatus().name()))
                                .build())
                        .toList()))
                .customerSentiment(customerSentiment(activityHistory, totalSpent))
                .purchasePrediction(purchasePrediction(orders.size(), totalSpent, activityHistory))
                .churnRisk(churnRisk(customer, activityHistory, lastOrderDate))
                .engagementScore(engagementScore(loginHistory.size(), activityHistory.size(), orders.size(), totalSpent))
                .recommendedProducts(recommendedProducts(customer, activityHistory))
                .highValueBadge(totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) >= 0 ? "High-value customer" : null)
                .build();
    }

    @Override
    @Transactional
    public CustomerDetailsResponse updateCustomerEngagement(UUID customerId, CustomerEngagementUpdateRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setAnniversaryDate(request.getAnniversaryDate());
        customer.setGender(trimToNull(request.getGender()));
        customer.setSpouseName(trimToNull(request.getSpouseName()));
        customer.setPreferredLanguage(trimToNull(request.getPreferredLanguage()));
        customer.setPreferredCategories(trimToNull(request.getPreferredCategories()));
        customer.setPreferredProducts(trimToNull(request.getPreferredProducts()));
        customer.setPreferredBrands(trimToNull(request.getPreferredBrands()));
        customer.setPreferredPriceRange(trimToNull(request.getPreferredPriceRange()));
        customer.setShoppingInterests(trimToNull(request.getShoppingInterests()));
        customer.setCustomerNotes(trimToNull(request.getCustomerNotes()));
        customer.setCustomerTags(trimToNull(request.getCustomerTags()));
        if (request.getBirthdayReminderEnabled() != null) {
            customer.setBirthdayReminderEnabled(request.getBirthdayReminderEnabled());
        }
        if (request.getAnniversaryReminderEnabled() != null) {
            customer.setAnniversaryReminderEnabled(request.getAnniversaryReminderEnabled());
        }
        customerRepository.save(customer);
        return getCustomerDetails(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseHistoryResponse> getPurchaseHistory(String mobile) {
        Customer customer = findCustomerByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(invoice -> PurchaseHistoryResponse.builder()
                        .invoiceId(invoice.getId())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .finalAmount(invoice.getFinalAmount())
                        .createdAt(invoice.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerLookupResponse lookupCustomer(String mobile) {
        Customer customer = findCustomerByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        var invoices = invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        return CustomerLookupResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .verificationStatus(verificationStatus(customer))
                .customerSource(customer.getCustomerSource())
                .loginEnabled(customer.isLoginEnabled())
                .totalInvoices(invoices.size())
                .totalSpent(invoices.stream()
                        .map(invoice -> Optional.ofNullable(invoice.getFinalAmount()).orElse(java.math.BigDecimal.ZERO))
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .lastPurchaseAt(invoices.stream()
                        .findFirst()
                        .map(invoice -> invoice.getCreatedAt())
                        .orElse(null))
                .build();
    }

    @Override
    @Transactional
    public Customer findOrCreateCustomer(String name, String mobile) {
        String normalizedMobile = normalizedLastTen(mobile);
        if (normalizedMobile.isBlank()) {
            throw new BusinessException("Valid mobile number is required");
        }
        return findCustomerByMobile(normalizedMobile)
                .map(existing -> {
                    if (!isVerified(existing) && !hasText(existing.getName()) && hasText(name)) {
                        existing.setName(name);
                    }
                    existing.setCustomerSource(mergeCustomerSource(existing.getCustomerSource(), "BILLING"));
                    if (!isVerified(existing)) {
                        markUnverified(existing);
                    }
                    existing.setLastOrderAt(LocalDateTime.now());
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setName(name);
                    customer.setMobile(normalizedMobile);
                    customer.setCustomerSource("BILLING");
                    markUnverified(customer);
                    customer.setLastOrderAt(LocalDateTime.now());
                    return customerRepository.save(customer);
                });
    }

    @Override
    @Transactional
    public void recordLogin(UUID customerId, String method, String status, HttpServletRequest request) {
        if (customerId == null) {
            return;
        }
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return;
        }
        String userAgent = request == null ? "" : Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
        CustomerLoginHistory history = new CustomerLoginHistory();
        history.setCustomerId(customerId);
        history.setLoginMethod(firstNonBlank(method, customer.getAuthProvider(), "Mobile OTP"));
        history.setStatus(firstNonBlank(status, "SUCCESS"));
        history.setIpAddress(clientIp(request));
        history.setSourcePage(request == null ? null : trimToNull(request.getHeader("X-Source-Page")));
        history.setDeviceType(deviceType(userAgent));
        history.setBrowser(browserName(userAgent));
        history.setOperatingSystem(operatingSystem(userAgent));
        loginHistoryRepository.save(history);

        customer.setLastLoginAt(history.getLoginAt());
        customer.setLastLoginMethod(history.getLoginMethod());
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void recordActivity(UUID customerId, CustomerActivityTrackRequest request) {
        if (customerId == null || request == null) {
            return;
        }
        CustomerActivityHistory activity = new CustomerActivityHistory();
        activity.setCustomerId(customerId);
        activity.setActivityType(firstNonBlank(normalizeActivityType(request.getActivityType()), "VIEW"));
        activity.setSearchKeyword(trimToNull(request.getSearchKeyword()));
        activity.setCategory(firstNonBlank(request.getCategory(), request.getSelectedCategory()));
        activity.setFilterUsed(trimToNull(request.getFilterUsed()));
        activity.setPriceRange(trimToNull(request.getPriceRange()));
        activity.setProductId(request.getProductId());
        activity.setProductName(trimToNull(request.getProductName()));
        activity.setResultCount(request.getResultCount());
        activity.setClickedProduct(firstNonBlank(request.getClickedProduct(), request.getProductName()));
        activity.setTimeSpentSeconds(request.getTimeSpentSeconds());
        activity.setCampaignSource(trimToNull(request.getCampaignSource()));
        activity.setPage(firstNonBlank(request.getPage(), request.getSourcePage()));
        activityHistoryRepository.save(activity);
    }

    @Override
    @Transactional
    public void recordLocation(UUID customerId, CustomerLocationUpdateRequest request) {
        if (customerId == null || request == null) {
            return;
        }
        CustomerLocationHistory location = new CustomerLocationHistory();
        location.setCustomerId(customerId);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setCity(trimToNull(request.getCity()));
        location.setState(trimToNull(request.getState()));
        location.setCountry(trimToNull(request.getCountry()));
        location.setPincode(trimToNull(request.getPincode()));
        location.setAccuracyMeters(request.getAccuracyMeters());
        location.setLocationSource(firstNonBlank(request.getLocationSource(), "GPS"));
        locationHistoryRepository.save(location);

        customerRepository.findById(customerId).ifPresent(customer -> {
            customer.setLastKnownLocation(formatLocation(location));
            customerRepository.save(customer);
        });
    }

    @Override
    @Transactional
    public CustomerSupportChatResponse startSupportChat(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        String normalizedMobile = normalizedLastTen(customer.getMobile());
        if (normalizedMobile.isBlank()) {
            throw new BusinessException("Customer mobile number is required to start WhatsApp support");
        }
        String externalUserId = "91" + normalizedMobile;
        OmnichannelLead lead = omnichannelLeadRepository.findFirstByChannelAndExternalUserIdOrderByUpdatedAtDesc("WHATSAPP", externalUserId)
                .orElseGet(() -> {
                    OmnichannelLead created = new OmnichannelLead();
                    created.setChannel("WHATSAPP");
                    created.setExternalUserId(externalUserId);
                    return created;
                });
        lead.setCustomerName(firstNonBlank(customer.getName(), "Customer"));
        lead.setMobile(firstNonBlank(customer.getMobile(), externalUserId));
        String greeting = crmGreetingMessage(customer);
        lead.setLatestMessage(greeting);
        lead.setStatus("NEW");
        OmnichannelLead savedLead = omnichannelLeadRepository.save(lead);

        OmnichannelConversation conversation = conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(savedLead.getId(), "WHATSAPP")
                .filter(existing -> Set.of("OPEN", "IN_PROGRESS").contains(firstNonBlank(existing.getStatus(), "").toUpperCase(Locale.ROOT)))
                .orElseGet(() -> {
                    OmnichannelConversation created = new OmnichannelConversation();
                    created.setLead(savedLead);
                    created.setChannel("WHATSAPP");
                    created.setExternalThreadId(externalUserId);
                    created.setStatus("OPEN");
                    return created;
                });
        conversation.setStatus(firstNonBlank(conversation.getStatus(), "OPEN"));
        OmnichannelConversation savedConversation = conversationRepository.save(conversation);

        OmnichannelConversationMessage note = new OmnichannelConversationMessage();
        note.setConversation(savedConversation);
        note.setDirection("OUTBOUND");
        note.setMessageType("TEXT");
        note.setMessageText(greeting);
        messageRepository.save(note);

        CustomerActivityHistory chatActivity = new CustomerActivityHistory();
        chatActivity.setCustomerId(customer.getId());
        chatActivity.setActivityType("CHAT_STARTED");
        chatActivity.setPage("/app/customers");
        chatActivity.setCampaignSource("Customer CRM");
        activityHistoryRepository.save(chatActivity);

        return CustomerSupportChatResponse.builder()
                .conversationId(savedConversation.getId())
                .status(savedConversation.getStatus())
                .customerName(savedLead.getCustomerName())
                .mobile(savedLead.getMobile())
                .build();
    }

    @Override
    @Transactional
    public void deleteCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).forEach(invoice -> {
            restoreInvoiceStock(invoice);
            invoiceItemRepository.deleteByInvoiceId(invoice.getId());
            customerOrderRepository.findByInvoiceId(invoice.getId()).ifPresent(customerOrderRepository::delete);
            invoiceRepository.delete(invoice);
        });

        customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).forEach(order -> {
            if (order.getSource() != OrderSource.BILLING || order.getInvoiceId() == null) {
                order.getItems().forEach(item -> {
                    Product product = item.getProduct();
                    if (product != null && item.getQuantity() != null) {
                        product.setQuantity(product.getQuantity() + item.getQuantity());
                        productRepository.save(product);
                    }
                });
            }
            customerOrderRepository.delete(order);
        });

        customerRepository.delete(customer);
    }

    private void restoreInvoiceStock(Invoice invoice) {
        invoice.getItems().forEach(item -> {
            Product product = item.getProduct();
            if (product != null && item.getQuantity() != null) {
                product.setQuantity(product.getQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        });
    }

    private void markUnverified(Customer customer) {
        customer.setMobileVerified(false);
        customer.setVerificationStatus("UNVERIFIED");
        customer.setLoginEnabled(false);
    }

    private boolean isVerified(Customer customer) {
        return customer != null
                && (customer.isMobileVerified() || "VERIFIED".equalsIgnoreCase(customer.getVerificationStatus()));
    }

    private String verificationStatus(Customer customer) {
        return isVerified(customer) ? "VERIFIED" : "UNVERIFIED";
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .email(customer.getEmail())
                .dateOfBirth(customer.getDateOfBirth())
                .anniversaryDate(customer.getAnniversaryDate())
                .customerTags(customer.getCustomerTags())
                .verificationStatus(verificationStatus(customer))
                .customerSource(customer.getCustomerSource())
                .loginEnabled(customer.isLoginEnabled())
                .otpVerifiedAt(customer.getOtpVerifiedAt())
                .lastOrderAt(customer.getLastOrderAt())
                .segments(buildSegments(customer, 0, BigDecimal.ZERO, List.of()))
                .createdAt(customer.getCreatedAt())
                .build();
    }

    private boolean customerMatchesSegment(CustomerResponse response, String normalizedSegment) {
        if ("verified customers".equals(normalizedSegment) || "verified".equals(normalizedSegment)) {
            return "verified".equalsIgnoreCase(response.getVerificationStatus());
        }
        if ("unverified customers".equals(normalizedSegment) || "unverified".equals(normalizedSegment)) {
            return !"verified".equalsIgnoreCase(response.getVerificationStatus());
        }
        if ("billing-created customers".equals(normalizedSegment) || "billing created".equals(normalizedSegment)) {
            return "billing".equalsIgnoreCase(response.getCustomerSource())
                    || "both".equalsIgnoreCase(response.getCustomerSource());
        }
        if ("website signup customers".equals(normalizedSegment) || "website signup".equals(normalizedSegment)) {
            return "website_signup".equalsIgnoreCase(response.getCustomerSource())
                    || "website".equalsIgnoreCase(response.getCustomerSource())
                    || "both".equalsIgnoreCase(response.getCustomerSource());
        }
        return response.getSegments() != null
                && response.getSegments().stream().map(this::normalizeLabel).anyMatch(normalizedSegment::equals);
    }

    private CustomerDetailsResponse.LoginSummary mapLogin(CustomerLoginHistory history) {
        return CustomerDetailsResponse.LoginSummary.builder()
                .loginTime(history.getLoginAt())
                .loginMethod(history.getLoginMethod())
                .device(history.getDeviceType())
                .browser(history.getBrowser())
                .ip(history.getIpAddress())
                .location(firstNonBlank(history.getLocation(), "—"))
                .status(history.getStatus())
                .build();
    }

    private CustomerDetailsResponse.LocationSummary mapLocation(CustomerLocationHistory location) {
        return CustomerDetailsResponse.LocationSummary.builder()
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .city(location.getCity())
                .state(location.getState())
                .country(location.getCountry())
                .pincode(location.getPincode())
                .accuracyMeters(location.getAccuracyMeters())
                .locationSource(location.getLocationSource())
                .createdAt(location.getCreatedAt())
                .build();
    }

    private CustomerDetailsResponse.ActivitySummary mapActivity(CustomerActivityHistory activity) {
        return CustomerDetailsResponse.ActivitySummary.builder()
                .createdAt(activity.getCreatedAt())
                .activityType(activity.getActivityType())
                .searchKeyword(activity.getSearchKeyword())
                .category(activity.getCategory())
                .filterUsed(activity.getFilterUsed())
                .priceRange(activity.getPriceRange())
                .productId(activity.getProductId())
                .productName(activity.getProductName())
                .timeSpentSeconds(activity.getTimeSpentSeconds())
                .resultCount(activity.getResultCount())
                .clickedProduct(activity.getClickedProduct())
                .campaignSource(activity.getCampaignSource())
                .page(activity.getPage())
                .build();
    }

    private List<String> buildPreferenceInsights(Customer customer,
                                                 List<CustomerActivityHistory> activities,
                                                 long totalOrders,
                                                 BigDecimal totalSpent) {
        List<String> insights = new ArrayList<>();
        addInsight(insights, customer.getPreferredCategories(), "Likes ");
        addInsight(insights, customer.getPreferredProducts(), "Prefers ");
        addInsight(insights, customer.getPreferredBrands(), "Brand affinity ");
        addInsight(insights, customer.getPreferredPriceRange(), "Budget range ");
        addInsight(insights, customer.getShoppingInterests(), "Interested in ");
        activities.stream()
                .map(CustomerActivityHistory::getCategory)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.groupingBy(value -> value, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(java.util.Map.Entry.comparingByValue())
                .ifPresent(entry -> insights.add("Recently interested in " + entry.getKey()));
        activities.stream()
                .filter(activity -> "ADD_TO_CART".equalsIgnoreCase(activity.getActivityType()))
                .findFirst()
                .ifPresent(activity -> insights.add("Added " + firstNonBlank(activity.getProductName(), "a product") + " to cart"));
        if (totalOrders > 1) {
            insights.add("Returning customer");
        }
        if (totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            insights.add("High value customer");
        }
        if (insights.isEmpty()) {
            insights.add("No preference pattern yet");
        }
        return insights.stream().distinct().limit(8).toList();
    }

    private List<String> buildSegments(Customer customer,
                                       long totalOrders,
                                       BigDecimal totalSpent,
                                       List<CustomerActivityHistory> activities) {
        List<String> segments = new ArrayList<>();
        segments.add(totalOrders > 0 ? "Returning Customer" : "New Customer");
        if (totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            segments.add("High Value Customer");
        }
        if (containsIgnoreCase(customer.getCustomerTags(), "offer")
                || activities.stream().anyMatch(activity -> containsIgnoreCase(activity.getActivityType(), "OFFER"))) {
            segments.add("Offer Interested Customer");
        }
        if (activities.stream().anyMatch(activity -> "ADD_TO_CART".equalsIgnoreCase(activity.getActivityType()))) {
            segments.add("Cart Abandoned Customer");
        }
        if (dateInNext30Days(customer.getDateOfBirth())) {
            segments.add("Birthday Upcoming");
        }
        if (dateInNext30Days(customer.getAnniversaryDate())) {
            segments.add("Anniversary Upcoming");
        }
        if (activities.size() >= 5) {
            segments.add("Frequent Viewer");
        }
        if (totalOrders >= 3) {
            segments.add("Frequent Buyer");
        }
        return segments.stream().distinct().toList();
    }

    private LocalDateTime latestActiveAt(Customer customer,
                                         List<CustomerLoginHistory> loginHistory,
                                         List<CustomerActivityHistory> activityHistory,
                                         LocalDateTime lastOrderDate) {
        return Stream.of(
                        customer.getLastLoginAt(),
                        lastOrderDate,
                        loginHistory.stream().findFirst().map(CustomerLoginHistory::getLoginAt).orElse(null),
                        activityHistory.stream().findFirst().map(CustomerActivityHistory::getCreatedAt).orElse(null),
                        customer.getUpdatedAt(),
                        customer.getCreatedAt()
                )
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private int engagementScore(int loginCount, int activityCount, long totalOrders, BigDecimal totalSpent) {
        int score = 24;
        score += Math.min(loginCount * 6, 24);
        score += Math.min(activityCount * 3, 30);
        score += Math.min((int) totalOrders * 8, 24);
        if (totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            score += 12;
        }
        return Math.min(score, 100);
    }

    private String customerSentiment(List<CustomerActivityHistory> activities, BigDecimal totalSpent) {
        if (totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            return "Premium intent";
        }
        boolean supportOrCart = activities.stream().anyMatch(activity ->
                "ADD_TO_CART".equalsIgnoreCase(activity.getActivityType())
                        || "CHAT_STARTED".equalsIgnoreCase(activity.getActivityType()));
        return supportOrCart ? "Warm and engaged" : "Learning";
    }

    private String purchasePrediction(long totalOrders, BigDecimal totalSpent, List<CustomerActivityHistory> activities) {
        if (totalOrders >= 3 || (totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(15000)) >= 0)) {
            return "High repeat purchase likelihood";
        }
        if (activities.stream().anyMatch(activity -> "ADD_TO_CART".equalsIgnoreCase(activity.getActivityType()))) {
            return "Likely to buy with timely follow-up";
        }
        if (activities.stream().anyMatch(activity -> "SEARCH".equalsIgnoreCase(activity.getActivityType()))) {
            return "Browsing intent detected";
        }
        return "Needs more behavior data";
    }

    private String churnRisk(Customer customer, List<CustomerActivityHistory> activities, LocalDateTime lastOrderDate) {
        LocalDateTime lastSignal = Stream.of(customer.getLastLoginAt(), lastOrderDate,
                        activities.stream().findFirst().map(CustomerActivityHistory::getCreatedAt).orElse(null))
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(customer.getCreatedAt());
        if (lastSignal == null) {
            return "Unknown";
        }
        if (lastSignal.isBefore(LocalDateTime.now().minusDays(90))) {
            return "High";
        }
        if (lastSignal.isBefore(LocalDateTime.now().minusDays(30))) {
            return "Medium";
        }
        return "Low";
    }

    private List<String> recommendedProducts(Customer customer, List<CustomerActivityHistory> activities) {
        List<String> recommendations = new ArrayList<>();
        addRecommendation(recommendations, customer.getPreferredProducts());
        addRecommendation(recommendations, customer.getPreferredCategories());
        activities.stream()
                .map(CustomerActivityHistory::getCategory)
                .filter(this::hasText)
                .limit(4)
                .forEach(value -> recommendations.add(value + " new arrivals"));
        if (recommendations.isEmpty()) {
            recommendations.add("Pearl jewelry bestsellers");
            recommendations.add("Festival cosmetics picks");
            recommendations.add("Budget-friendly gifting");
        }
        return recommendations.stream().distinct().limit(5).toList();
    }

    private void addRecommendation(List<String> recommendations, String value) {
        if (!hasText(value)) {
            return;
        }
        Stream.of(value.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .limit(3)
                .forEach(recommendations::add);
    }

    private String crmGreetingMessage(Customer customer) {
        String nameLine = hasText(customer.getName()) ? "Namaste " + customer.getName().trim() + "," : "Namaste,";
        return "👋 " + nameLine + "\n\n"
                + "Welcome to Krishnai Pearl Shopee!\n\n"
                + "We are happy to assist you today.\n\n"
                + "I can help you with:\n"
                + "- Jewellery recommendations\n"
                + "- Cosmetics suggestions\n"
                + "- Orders\n"
                + "- Offers\n"
                + "- Support\n\n"
                + "Before we begin, may I know your preferred language";
    }

    private List<CustomerDetailsResponse.TimelineEvent> buildTimeline(Customer customer,
                                                                      List<CustomerLoginHistory> loginHistory,
                                                                      List<CustomerActivityHistory> activityHistory,
                                                                      List<CustomerDetailsResponse.TimelineEvent> orderEvents) {
        List<CustomerDetailsResponse.TimelineEvent> timeline = new ArrayList<>();
        timeline.add(CustomerDetailsResponse.TimelineEvent.builder()
                .createdAt(customer.getCreatedAt())
                .type("PROFILE")
                .title("Customer registered")
                .detail(firstNonBlank(customer.getCustomerSource(), "Customer"))
                .build());
        loginHistory.stream().limit(8).map(login -> CustomerDetailsResponse.TimelineEvent.builder()
                        .createdAt(login.getLoginAt())
                        .type("LOGIN")
                        .title("Logged in")
                        .detail(firstNonBlank(login.getLoginMethod(), "Customer login") + " · " + firstNonBlank(login.getStatus(), "SUCCESS"))
                        .build())
                .forEach(timeline::add);
        activityHistory.stream().limit(12).map(activity -> CustomerDetailsResponse.TimelineEvent.builder()
                        .createdAt(activity.getCreatedAt())
                        .type(firstNonBlank(activity.getActivityType(), "ACTIVITY"))
                        .title(activityTitle(activity))
                        .detail(firstNonBlank(activity.getSearchKeyword(), activity.getProductName(), activity.getCategory(), activity.getPage(), "Customer activity"))
                        .build())
                .forEach(timeline::add);
        timeline.addAll(orderEvents);
        return timeline.stream()
                .sorted(Comparator.comparing(CustomerDetailsResponse.TimelineEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(30)
                .toList();
    }

    private String activityTitle(CustomerActivityHistory activity) {
        String type = firstNonBlank(activity.getActivityType(), "Activity").replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private String resolveSupportStatus(Customer customer) {
        String normalized = normalizedLastTen(customer.getMobile());
        if (normalized.isBlank()) {
            return "No mobile";
        }
        return omnichannelLeadRepository.findFirstByChannelAndExternalUserIdOrderByUpdatedAtDesc("WHATSAPP", "91" + normalized)
                .flatMap(lead -> conversationRepository.findFirstByLead_IdAndChannelOrderByUpdatedAtDesc(lead.getId(), "WHATSAPP"))
                .map(conversation -> firstNonBlank(conversation.getStatus(), "OPEN"))
                .orElse("No active chat");
    }

    private void addInsight(List<String> insights, String value, String prefix) {
        if (hasText(value)) {
            insights.add(prefix + value);
        }
    }

    private boolean dateInNext30Days(LocalDate date) {
        if (date == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        MonthDay target = MonthDay.from(date);
        LocalDate thisYear = target.atYear(today.getYear());
        if (thisYear.isBefore(today)) {
            thisYear = target.atYear(today.getYear() + 1);
        }
        return !thisYear.isAfter(today.plusDays(30));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = firstNonBlank(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"), request.getRemoteAddr());
        if (forwarded != null && forwarded.contains(",")) {
            return forwarded.split(",")[0].trim();
        }
        return forwarded;
    }

    private String deviceType(String userAgent) {
        String normalized = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("mobile") || normalized.contains("android") || normalized.contains("iphone")) {
            return "Mobile";
        }
        if (normalized.contains("ipad") || normalized.contains("tablet")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private String browserName(String userAgent) {
        String normalized = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("edg/")) return "Edge";
        if (normalized.contains("chrome/")) return "Chrome";
        if (normalized.contains("safari/")) return "Safari";
        if (normalized.contains("firefox/")) return "Firefox";
        return "Unknown";
    }

    private String operatingSystem(String userAgent) {
        String normalized = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("android")) return "Android";
        if (normalized.contains("iphone") || normalized.contains("ipad")) return "iOS";
        if (normalized.contains("mac os")) return "macOS";
        if (normalized.contains("windows")) return "Windows";
        if (normalized.contains("linux")) return "Linux";
        return "Unknown";
    }

    private String normalizeActivityType(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT).replace(' ', '_') : null;
    }

    private String formatLocation(CustomerLocationHistory location) {
        if (location == null) {
            return null;
        }
        String place = String.join(", ", Stream.of(location.getCity(), location.getState(), location.getCountry())
                .filter(this::hasText)
                .toList());
        if (hasText(place)) {
            return place;
        }
        if (location.getLatitude() != null && location.getLongitude() != null) {
            return location.getLatitude() + ", " + location.getLongitude();
        }
        return "Location not shared";
    }

    private String currencyText(BigDecimal amount) {
        return "₹" + Optional.ofNullable(amount).orElse(BigDecimal.ZERO);
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return hasText(haystack) && hasText(needle) && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String latestAddress(java.util.UUID customerId) {
        return addressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .findFirst()
                .map(this::formatAddress)
                .orElse("");
    }

    private Optional<Customer> findCustomerByMobile(String mobile) {
        String normalized = normalizedLastTen(mobile);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return customerRepository.findByNormalizedMobile(normalized)
                .or(() -> customerRepository.findByMobile(mobile));
    }

    private String normalizedLastTen(String mobile) {
        return MobileNumberUtils.normalizeIndianMobile(mobile);
    }

    private String mergeCustomerSource(String current, String incoming) {
        String safeCurrent = current == null || current.isBlank() ? incoming : current.trim().toUpperCase();
        String safeIncoming = incoming == null || incoming.isBlank() ? safeCurrent : incoming.trim().toUpperCase();
        if (safeCurrent.equals(safeIncoming)) {
            return safeCurrent;
        }
        return "BOTH";
    }

    private String formatAddress(Address address) {
        return String.join(", ", List.of(
                        address.getRecipientName(),
                        address.getLine1(),
                        Optional.ofNullable(address.getLine2()).orElse(""),
                        Optional.ofNullable(address.getLandmark()).orElse(""),
                        address.getCity(),
                        address.getState(),
                        address.getPincode()
                ).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }
}
