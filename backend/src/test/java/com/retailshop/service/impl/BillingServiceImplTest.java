package com.retailshop.service.impl;

import com.retailshop.dto.InvoiceCreateRequest;
import com.retailshop.dto.InvoiceItemRequest;
import com.retailshop.entity.Customer;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.Product;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.PaymentMode;
import com.retailshop.exception.BusinessException;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceItemRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CustomerService;
import com.retailshop.service.PaymentService;
import com.retailshop.service.PaymentTransactionService;
import com.retailshop.service.StaffUserService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private StaffUserService staffUserService;

    @Mock
    private OrderPricingService orderPricingService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private BillingServiceImpl billingService;

    private Product product;
    private Customer customer;
    private StaffUser salesPerson;

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

        salesPerson = new StaffUser();
        salesPerson.setId(UUID.randomUUID());
        salesPerson.setDisplayName("Rutuja");

        lenient().when(customerOrderRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
        lenient().when(customerOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(staffUserService.getActiveSalesPerson(salesPerson.getId())).thenReturn(salesPerson);
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
        request.setSalesPersonUserId(salesPerson.getId());
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
        assertEquals("Rutuja", response.getSalesPersonName());
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
        request.setSalesPersonUserId(salesPerson.getId());
        request.setItems(List.of(line));
        request.setPaymentMode(PaymentMode.CASH);
        request.setManualDiscount(BigDecimal.valueOf(1000));

        when(customerService.findOrCreateCustomer(customer.getName(), customer.getMobile())).thenReturn(customer);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 1)), eq(null))).thenReturn(pricingResult(1, BigDecimal.ZERO));

        assertThrows(BusinessException.class, () -> billingService.createInvoice(request));
    }

    @Test
    void shouldIncludeConfiguredGstInShopBillingInvoice() {
        InvoiceItemRequest line = new InvoiceItemRequest();
        line.setProductId(product.getId());
        line.setQuantity(2);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setCustomerName(customer.getName());
        request.setCustomerMobile(customer.getMobile());
        request.setSalesPersonUserId(salesPerson.getId());
        request.setItems(List.of(line));
        request.setPaymentMode(PaymentMode.CASH);
        request.setManualDiscount(BigDecimal.ZERO);

        when(customerService.findOrCreateCustomer(customer.getName(), customer.getMobile())).thenReturn(customer);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 2)), eq(null))).thenReturn(
                pricingResult(2, BigDecimal.ZERO, BigDecimal.valueOf(53.91), BigDecimal.valueOf(53.91))
        );
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(UUID.randomUUID());
            return invoice;
        });
        when(invoiceItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = billingService.createInvoice(request);

        assertEquals(BigDecimal.valueOf(107.82).setScale(2), response.getTax());
        assertEquals(BigDecimal.valueOf(53.91).setScale(2), response.getCgst());
        assertEquals(BigDecimal.valueOf(53.91).setScale(2), response.getSgst());
        assertEquals(BigDecimal.valueOf(1305.82).setScale(2), response.getFinalAmount());
    }

    @Test
    void shouldRejectUnverifiedRazorpayUpiBeforeReducingStock() {
        InvoiceItemRequest line = new InvoiceItemRequest();
        line.setProductId(product.getId());
        line.setQuantity(1);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setCustomerName(customer.getName());
        request.setCustomerMobile(customer.getMobile());
        request.setSalesPersonUserId(salesPerson.getId());
        request.setItems(List.of(line));
        request.setPaymentMode(PaymentMode.UPI);
        request.setManualDiscount(BigDecimal.ZERO);
        request.setRazorpayOrderId("order_shop_123");
        request.setRazorpayPaymentId("pay_shop_123");
        request.setRazorpaySignature("bad-signature");

        when(staffUserService.getActiveSalesPerson(salesPerson.getId())).thenReturn(salesPerson);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 1)), eq(null))).thenReturn(pricingResult(1, BigDecimal.ZERO));
        when(paymentService.verifyPayment(any(), eq(BigDecimal.valueOf(599.00).setScale(2)))).thenReturn(false);

        assertThrows(BusinessException.class, () -> billingService.createInvoice(request));

        assertEquals(10, product.getQuantity());
        verify(invoiceRepository, never()).save(any());
        verify(invoiceItemRepository, never()).saveAll(any());
    }

    @Test
    void shouldLinkRazorpayDiagnosticsAfterSuccessfulShopUpiInvoice() {
        InvoiceItemRequest line = new InvoiceItemRequest();
        line.setProductId(product.getId());
        line.setQuantity(1);

        InvoiceCreateRequest request = new InvoiceCreateRequest();
        request.setCustomerName(customer.getName());
        request.setCustomerMobile(customer.getMobile());
        request.setSalesPersonUserId(salesPerson.getId());
        request.setItems(List.of(line));
        request.setPaymentMode(PaymentMode.UPI);
        request.setManualDiscount(BigDecimal.ZERO);
        request.setRazorpayOrderId("order_shop_456");
        request.setRazorpayPaymentId("pay_shop_456");
        request.setRazorpaySignature("signature");

        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderPricingService.priceProducts(eq(Map.of(product.getId(), 1)), eq(null))).thenReturn(pricingResult(1, BigDecimal.ZERO));
        when(paymentService.verifyPayment(any(), eq(BigDecimal.valueOf(599.00).setScale(2)))).thenReturn(true);
        when(customerService.findOrCreateCustomer(customer.getName(), customer.getMobile())).thenReturn(customer);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(UUID.randomUUID());
            return invoice;
        });
        when(invoiceItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = billingService.createInvoice(request);

        assertEquals(PaymentMode.UPI, response.getPaymentMode());
        assertEquals(9, product.getQuantity());
        verify(paymentTransactionService).linkOrder(eq("order_shop_456"), eq(response.getId()), eq(response.getInvoiceNumber()), eq(customer.getId()));
    }

    private OrderPricingResult pricingResult(int quantity, BigDecimal automaticDiscount) {
        return pricingResult(quantity, automaticDiscount, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private OrderPricingResult pricingResult(int quantity, BigDecimal automaticDiscount, BigDecimal cgst, BigDecimal sgst) {
        BigDecimal subtotal = product.getSellingPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2);
        BigDecimal tax = cgst.add(sgst).setScale(2);
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
                .tax(tax)
                .cgst(cgst.setScale(2))
                .sgst(sgst.setScale(2))
                .delivery(BigDecimal.ZERO.setScale(2))
                .finalTotal(subtotal.subtract(automaticDiscount).add(tax).setScale(2))
                .build();
    }
}
