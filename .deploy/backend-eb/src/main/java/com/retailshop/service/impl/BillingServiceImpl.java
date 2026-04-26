package com.retailshop.service.impl;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceItemResponse;
import com.retailshop.dto.InvoiceResponse;
import com.retailshop.dto.InvoiceSearchResponse;
import com.retailshop.entity.Customer;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.DiscountType;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.BillingService;
import com.retailshop.service.CustomerService;
import com.retailshop.service.OfferService;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final OfferService offerService;

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

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal offerDiscount = BigDecimal.ZERO;
        List<InvoiceItem> invoiceItems = new ArrayList<>();
        Map<UUID, Integer> normalizedItems = normalizeItems(request);

        for (Map.Entry<UUID, Integer> entry : normalizedItems.entrySet()) {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int requestedQuantity = entry.getValue();

            if (product.getQuantity() < requestedQuantity) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }

            BigDecimal unitPrice = product.getSellingPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            BigDecimal bestDiscount = offerService.calculateBestDiscount(product, requestedQuantity)
                    .setScale(2, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(lineTotal);
            offerDiscount = offerDiscount.add(bestDiscount);

            product.setQuantity(product.getQuantity() - requestedQuantity);
            productRepository.save(product);

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProduct(product);
            item.setQuantity(requestedQuantity);
            item.setPrice(unitPrice);
            item.setDiscount(bestDiscount);
            invoiceItems.add(item);
        }

        BigDecimal manualDiscount = calculateManualDiscount(totalAmount, request);
        invoice.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        invoice.setDiscount(offerDiscount.add(manualDiscount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal finalAmount = totalAmount.subtract(invoice.getDiscount());
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Discount cannot exceed total invoice amount");
        }
        invoice.setFinalAmount(finalAmount.setScale(2, RoundingMode.HALF_UP));

        Invoice savedInvoice = invoiceRepository.save(invoice);
        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(invoiceItems);
        savedInvoice.setItems(savedItems);
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
    public InvoiceSearchResponse searchInvoices(LocalDate fromDate, LocalDate toDate, String customerName) {
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
        List<Invoice> invoices = normalizedName == null
                ? invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                : invoiceRepository.findByCreatedAtBetweenAndCustomer_NameContainingIgnoreCaseOrderByCreatedAtDesc(start, end, normalizedName);

        return InvoiceSearchResponse.builder()
                .fromDate(rangeStart)
                .toDate(rangeEnd)
                .invoices(invoices.stream()
                        .map(this::mapToResponse)
                        .toList())
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

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal offerDiscount = BigDecimal.ZERO;
        List<InvoiceItem> invoiceItems = new ArrayList<>();
        Map<UUID, Integer> normalizedItems = normalizeItems(request);

        for (Map.Entry<UUID, Integer> entry : normalizedItems.entrySet()) {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int requestedQuantity = entry.getValue();

            if (product.getQuantity() < requestedQuantity) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }

            BigDecimal unitPrice = product.getSellingPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            BigDecimal bestDiscount = offerService.calculateBestDiscount(product, requestedQuantity)
                    .setScale(2, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(lineTotal);
            offerDiscount = offerDiscount.add(bestDiscount);

            if (persist) {
                product.setQuantity(product.getQuantity() - requestedQuantity);
                productRepository.save(product);
            }

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProduct(product);
            item.setQuantity(requestedQuantity);
            item.setPrice(unitPrice);
            item.setDiscount(bestDiscount);
            invoiceItems.add(item);
        }

        BigDecimal manualDiscount = calculateManualDiscount(totalAmount, request);
        invoice.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        invoice.setDiscount(offerDiscount.add(manualDiscount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal finalAmount = totalAmount.subtract(invoice.getDiscount());
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
}
