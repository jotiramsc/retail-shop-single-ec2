package com.retailshop.service.impl;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceItemResponse;
import com.retailshop.dto.InvoiceResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.BillingService;
import com.retailshop.service.CustomerService;
import com.retailshop.service.pricing.OrderPricingResult;
import com.retailshop.service.pricing.OrderPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerService customerService;
    private final OrderPricingService orderPricingService;

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse previewInvoice(InvoiceCreateRequest request) {
        return buildInvoiceResponse(request, false);
    }

    @Override
    @Transactional
    public InvoiceResponse createInvoice(InvoiceCreateRequest request) {
        return buildInvoiceResponse(request, true);
    }

    @Override
    @Transactional
    public InvoiceResponse updateInvoice(UUID id, InvoiceCreateRequest request) {
        Invoice invoice = invoiceRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        for (InvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        invoiceItemRepository.deleteByInvoiceId(invoice.getId());
        invoice.getItems().clear();

        Customer customer = customerService.findOrCreateCustomer(request.getCustomerName(), request.getCustomerMobile());
        invoice.setCustomer(customer);
        invoice.setPaymentMode(request.getPaymentMode());
        String normalizedCouponCode = normalizeCouponCode(request.getCouponCode());

        Map<UUID, Integer> normalizedItems = normalizeItems(request);
        OrderPricingResult pricing = orderPricingService.priceProducts(normalizedItems, normalizedCouponCode);
        BigDecimal totalAmount = pricing.getSubtotal();
        List<InvoiceItem> invoiceItems = new ArrayList<>();

        pricing.getItems().forEach(pricingItem -> {
            Product product = productRepository.findById(pricingItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int requestedQuantity = pricingItem.getQuantity();

            if (product.getQuantity() < requestedQuantity) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }

            product.setQuantity(product.getQuantity() - requestedQuantity);
            productRepository.save(product);

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProduct(product);
            item.setQuantity(requestedQuantity);
            item.setPrice(pricingItem.getUnitPrice());
            item.setDiscount(normalizedCouponCode == null ? pricingItem.getAutomaticDiscount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            invoiceItems.add(item);
        });
        BigDecimal manualDiscount = calculateManualDiscount(totalAmount, request);
        invoice.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        invoice.setCouponCode(normalizedCouponCode);
        invoice.setDiscount(pricing.getDiscount().add(manualDiscount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal finalAmount = totalAmount
                .subtract(invoice.getDiscount())
                .add(pricing.getTax())
                .add(pricing.getDelivery());
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Discount cannot exceed total invoice amount");
        }
        invoice.setFinalAmount(finalAmount.setScale(2, RoundingMode.HALF_UP));

        Invoice savedInvoice = invoiceRepository.save(invoice);
        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(invoiceItems);
        savedInvoice.setItems(savedItems);
        upsertBillingOrder(savedInvoice, savedItems);
        return mapToResponse(savedInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        return mapToResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceSearchResponse searchInvoices(LocalDate fromDate, LocalDate toDate, String customerName, Pageable pageable) {
        LocalDate rangeEnd = toDate != null ? toDate : LocalDate.now();
        LocalDate rangeStart = fromDate != null ? fromDate : rangeEnd;
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate swap = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = swap;
        }
        LocalDateTime start = rangeStart.atStartOfDay();
        LocalDateTime end = rangeEnd.atTime(LocalTime.MAX);
        String normalizedName = customerName == null || customerName.isBlank() ? null : customerName.trim();
        Page<Invoice> invoices = normalizedName == null
                ? invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable)
                : invoiceRepository.findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(start, end, normalizedName, pageable);

        return InvoiceSearchResponse.builder()
                .fromDate(rangeStart)
                .toDate(rangeEnd)
                .invoices(invoices.getContent().stream()
                        .map(this::mapToResponse)
                        .toList())
                .page(invoices.getNumber())
                .size(invoices.getSize())
                .totalItems(invoices.getTotalElements())
                .totalPages(invoices.getTotalPages())
                .hasNext(invoices.hasNext())
                .hasPrevious(invoices.hasPrevious())
                .build();
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .customerMobile(invoice.getCustomer().getMobile())
                .totalAmount(invoice.getTotalAmount())
                .discount(invoice.getDiscount())
                .finalAmount(invoice.getFinalAmount())
                .paymentMode(invoice.getPaymentMode())
                .couponCode(invoice.getCouponCode())
                .createdAt(invoice.getCreatedAt())
                .items(invoice.getItems().stream()
                        .map(item -> InvoiceItemResponse.builder()
                                .productId(item.getProduct().getId())
                                .productName(item.getProduct().getName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getPrice())
                                .discount(item.getDiscount())
                                .lineTotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())).subtract(item.getDiscount()))
                                .build())
                        .toList())
                .build();
    }

    private InvoiceResponse buildInvoiceResponse(InvoiceCreateRequest request, boolean persist) {
        Customer customer = persist
                ? customerService.findOrCreateCustomer(request.getCustomerName(), request.getCustomerMobile())
                : buildPreviewCustomer(request);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(persist
                ? "INV-" + LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : "PREVIEW");
        invoice.setCustomer(customer);
        invoice.setPaymentMode(request.getPaymentMode());
        String normalizedCouponCode = normalizeCouponCode(request.getCouponCode());

        Map<UUID, Integer> normalizedItems = normalizeItems(request);
        OrderPricingResult pricing = orderPricingService.priceProducts(normalizedItems, normalizedCouponCode);
        BigDecimal totalAmount = pricing.getSubtotal();
        List<InvoiceItem> invoiceItems = new ArrayList<>();

        pricing.getItems().forEach(pricingItem -> {
            Product product = productRepository.findById(pricingItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int requestedQuantity = pricingItem.getQuantity();

            if (product.getQuantity() < requestedQuantity) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }

            if (persist) {
                product.setQuantity(product.getQuantity() - requestedQuantity);
                productRepository.save(product);
            }

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProduct(product);
            item.setQuantity(requestedQuantity);
            item.setPrice(pricingItem.getUnitPrice());
            item.setDiscount(normalizedCouponCode == null ? pricingItem.getAutomaticDiscount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            invoiceItems.add(item);
        });
        BigDecimal manualDiscount = calculateManualDiscount(totalAmount, request);
        invoice.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        invoice.setCouponCode(normalizedCouponCode);
        invoice.setDiscount(pricing.getDiscount().add(manualDiscount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal finalAmount = totalAmount
                .subtract(invoice.getDiscount())
                .add(pricing.getTax())
                .add(pricing.getDelivery());
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Discount cannot exceed total invoice amount");
        }
        invoice.setFinalAmount(finalAmount.setScale(2, RoundingMode.HALF_UP));

        if (!persist) {
            invoice.setItems(invoiceItems);
            return mapToResponse(invoice);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);
        invoiceItems.forEach(item -> item.setInvoice(savedInvoice));
        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(invoiceItems);
        savedInvoice.setItems(savedItems);
        upsertBillingOrder(savedInvoice, savedItems);
        return mapToResponse(savedInvoice);
    }

    private Customer buildPreviewCustomer(InvoiceCreateRequest request) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setName(request.getCustomerName());
        customer.setMobile(request.getCustomerMobile());
        return customer;
    }

    private Map<UUID, Integer> normalizeItems(InvoiceCreateRequest request) {
        Map<UUID, Integer> normalizedItems = new LinkedHashMap<>();
        request.getItems().forEach(item ->
                normalizedItems.merge(item.getProductId(), item.getQuantity(), Integer::sum));
        return normalizedItems;
    }

    private BigDecimal calculateManualDiscount(BigDecimal totalAmount, InvoiceCreateRequest request) {
        if (normalizeCouponCode(request.getCouponCode()) != null
                && request.getManualDiscount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Custom discount cannot be combined with a selected coupon");
        }
        BigDecimal manualDiscount = request.getManualDiscount().setScale(2, RoundingMode.HALF_UP);
        if (request.getManualDiscountType() == DiscountType.PERCENT) {
            if (manualDiscount.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException("Percent discount cannot exceed 100%");
            }
            return totalAmount.multiply(manualDiscount)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return manualDiscount;
    }

    private void upsertBillingOrder(Invoice invoice, List<InvoiceItem> invoiceItems) {
        CustomerOrder order = customerOrderRepository.findByInvoiceId(invoice.getId())
                .orElseGet(CustomerOrder::new);

        order.setOrderNumber(invoice.getInvoiceNumber());
        order.setCustomer(invoice.getCustomer());
        order.setAddress(null);
        order.setSubtotal(invoice.getTotalAmount());
        order.setDiscount(invoice.getDiscount());
        order.setTax(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setDelivery(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setFinalAmount(invoice.getFinalAmount());
        order.setCouponCode(invoice.getCouponCode());
        order.setPaymentGateway(invoice.getPaymentMode().name());
        order.setPaymentOrderId(invoice.getInvoiceNumber());
        order.setPaymentId(invoice.getInvoiceNumber());
        order.setPaymentStatus("PAID");
        order.setSource(OrderSource.BILLING);
        order.setInvoiceId(invoice.getId());
        order.setStatus(OrderStatus.COMPLETED);
        order.getItems().clear();

        for (InvoiceItem item : invoiceItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(item.getProduct());
            orderItem.setProductName(item.getProduct().getName());
            orderItem.setSku(item.getProduct().getSku());
            orderItem.setCategory(item.getProduct().getCategory());
            orderItem.setPrice(item.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setLineTotal(
                    item.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
                            .subtract(item.getDiscount())
                            .setScale(2, RoundingMode.HALF_UP)
            );
            order.getItems().add(orderItem);
        }

        customerOrderRepository.save(order);
    }

    private String normalizeCouponCode(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        return couponCode.trim().toUpperCase(Locale.ROOT);
    }
}
