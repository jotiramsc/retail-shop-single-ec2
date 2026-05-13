package com.retailshop.service.bot.impl;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.bot.CustomerContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerContextServiceImpl implements CustomerContextService {

    private final CustomerRepository customerRepository;
    private final CustomerOrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;

    @Override
    public BotContext buildContext(String mobile) {
        String normalizedMobile = lastTenDigits(mobile);
        Optional<Customer> customer = normalizedMobile.isBlank()
                ? Optional.empty()
                : customerRepository.findByNormalizedMobile(normalizedMobile);
        List<CustomerOrder> orders = customer
                .map(value -> orderRepository.findByCustomerIdOrderByCreatedAtDesc(value.getId()))
                .orElseGet(() -> normalizedMobile.isBlank()
                        ? List.of()
                        : orderRepository.findTop3ByCustomer_MobileContainingOrderByCreatedAtDesc(normalizedMobile));
        BigDecimal totalOrderValue = orders.stream()
                .map(CustomerOrder::getFinalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Offer> activeOffers = offerRepository.findActiveOffers(LocalDate.now()).stream()
                .limit(8)
                .toList();
        List<String> categories = productRepository.findAll().stream()
                .map(Product::getCategory)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        return BotContext.builder()
                .customer(customer.orElse(null))
                .mobile(normalizedMobile)
                .recentOrders(orders.stream().limit(5).toList())
                .orderCount(orders.size())
                .totalOrderValue(totalOrderValue)
                .activeOffers(activeOffers)
                .categories(categories)
                .build();
    }

    private String lastTenDigits(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() <= 10) {
            return digits;
        }
        return digits.substring(digits.length() - 10);
    }
}
