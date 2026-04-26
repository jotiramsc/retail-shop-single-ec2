package com.retailshop.service.impl;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceItemRequest;
import com.retailshop.entity.Customer;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.PaymentMode;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CustomerService;
import com.retailshop.service.pricing.OrderPricingItem;
import com.retailshop.service.pricing.OrderPricingResult;
import com.retailshop.service.pricing.OrderPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private OrderPricingService orderPricingService;

    @InjectMocks
    private BillingServiceImpl billingService;

    private Product product;
    private Customer customer;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Hydra Glow Serum");
        product.setCategory("COSMETICS");
        product.setQuantity(10);
        product.setSellingPrice(BigDecimal.valueOf(599));

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setName("Anika Sharma");
        customer.setMobile("9876543210");

        lenient().when(customerOrderRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
        lenient().when(customerOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldNormalizeDuplicateInvoiceItemsBeforeReducingStock() {
        InvoiceItemRequest lineOne = new InvoiceItemRequest();
        lineOne.setProductId(product.getId());
        lineOne.setQuantity(2);

        InvoiceItemRequest lineTwo = new InvoiceItemRequest();
        lineTwo.setProductId(product.getId());
        lineTwo.setQuantity(3);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setCustomerName(customer.getName());
        request.setCustomerMobile(customer.getMobile());
        request.setItems(List.of(lineOne, lineTwo));
        request.setPaymentMode(PaymentMode.UPI);
        request.setManualDiscount(BigDecimal.TEN);

        when(customerService.findOrCreateCustomer(customer.getName(), customer.getMobile())).thenReturn(customer);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 5)), eq(null))).thenReturn(pricingResult(5, BigDecimal.valueOf(50)));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(UUID.randomUUID());
            return invoice;
        });
        when(invoiceItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<InvoiceItem> items = invocation.getArgument(0);
            items.forEach(item -> item.setId(UUID.randomUUID()));
            return items;
        });

        var response = billingService.createInvoice(request);

        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(2995.00).setScale(2), response.getTotalAmount());
        assertEquals(BigDecimal.valueOf(60.00).setScale(2), response.getDiscount());
        assertEquals(BigDecimal.valueOf(2935.00).setScale(2), response.getFinalAmount());
        assertEquals(5, product.getQuantity());
    }

    @Test
    void shouldRejectInvoiceWhenDiscountExceedsTotal() {
        InvoiceItemRequest line = new InvoiceItemRequest();
        line.setProductId(product.getId());
        line.setQuantity(1);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setCustomerName(customer.getName());
        request.setCustomerMobile(customer.getMobile());
        request.setItems(List.of(line));
        request.setPaymentMode(PaymentMode.CASH);
        request.setManualDiscount(BigDecimal.valueOf(1000));

        when(customerService.findOrCreateCustomer(customer.getName(), customer.getMobile())).thenReturn(customer);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 1)), eq(null))).thenReturn(pricingResult(1, BigDecimal.ZERO));

        assertThrows(BusinessException.class, () -> billingService.createInvoice(request));
    }

    private OrderPricingResult pricingResult(int quantity, BigDecimal automaticDiscount) {
        BigDecimal subtotal = product.getSellingPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2);
        return OrderPricingResult.builder()
                .items(List.of(OrderPricingItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .sku(product.getSku())
                        .category(product.getCategory())
                        .unitPrice(product.getSellingPrice())
                        .quantity(quantity)
                        .stockAvailable(product.getQuantity())
                        .lineTotal(subtotal)
                        .automaticDiscount(automaticDiscount.setScale(2))
                        .build()))
                .applicableOffers(List.of())
                .requestedCouponCode(null)
                .appliedCouponCode(null)
                .subtotal(subtotal)
                .automaticDiscount(automaticDiscount.setScale(2))
                .couponDiscount(BigDecimal.ZERO.setScale(2))
                .discount(automaticDiscount.setScale(2))
                .tax(BigDecimal.ZERO.setScale(2))
                .delivery(BigDecimal.ZERO.setScale(2))
                .finalTotal(subtotal.subtract(automaticDiscount).setScale(2))
                .build();
    }
}
