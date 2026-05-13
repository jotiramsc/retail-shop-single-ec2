package com.retailshop.service.impl;

import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import com.retailshop.enums.PaymentMode;
import com.retailshop.enums.StaffRole;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.StaffUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalespersonSalesServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private StaffUserService staffUserService;

    @InjectMocks
    private SalespersonSalesServiceImpl salespersonSalesService;

    @Test
    void shouldReturnWebsiteOnlySalesForAdminSelection() {
        Product product = product("Temple Necklace", "JEWELLERY", "JEW-100");

        CustomerOrder websiteOrder = new CustomerOrder();
        websiteOrder.setId(UUID.randomUUID());
        websiteOrder.setOrderNumber("WEB-101");
        websiteOrder.setSource(OrderSource.WEBSITE);
        websiteOrder.setStatus(OrderStatus.COMPLETED);
        websiteOrder.setSalesPersonName("Website");
        websiteOrder.setCustomer(customer("Riya"));
        websiteOrder.setPaymentGateway("RAZORPAY");
        websiteOrder.setFinalAmount(BigDecimal.valueOf(499.00).setScale(2));
        websiteOrder.setCreatedAt(LocalDateTime.of(2026, 4, 29, 11, 30));
        websiteOrder.setItems(List.of(orderItem(websiteOrder, product, 2)));

        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(websiteOrder));

        StaffUser admin = new StaffUser();
        admin.setId(UUID.randomUUID());
        admin.setRole(StaffRole.ADMIN);
        admin.setDisplayName("Store Admin");

        var response = salespersonSalesService.getSalespersonSales(admin, "WEBSITE", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "DAILY");

        assertEquals("Website", response.getSalespersonName());
        assertEquals(1, response.getTotalOrders());
        assertEquals(2, response.getTotalItemsSold());
        assertEquals(BigDecimal.valueOf(499.00).setScale(2), response.getTotalSalesAmount());
        assertEquals("WEB-101", response.getRecords().get(0).getBillNo());
        verify(staffUserService, never()).getActiveSalesPerson(any());
    }

    @Test
    void shouldLockStaffUserToOwnSales() {
        Product product = product("Cocoa Brow Kit", "COSMETICS", "COS-101");
        UUID salespersonId = UUID.randomUUID();

        Invoice ownInvoice = new Invoice();
        ownInvoice.setId(UUID.randomUUID());
        ownInvoice.setInvoiceNumber("INV-101");
        ownInvoice.setCustomer(customer("Anika"));
        ownInvoice.setSalesPersonUserId(salespersonId);
        ownInvoice.setSalesPersonName("Counter Cashier");
        ownInvoice.setPaymentMode(PaymentMode.CASH);
        ownInvoice.setFinalAmount(BigDecimal.valueOf(350.00).setScale(2));
        ownInvoice.setCreatedAt(LocalDateTime.of(2026, 4, 28, 14, 0));
        ownInvoice.setItems(List.of(invoiceItem(ownInvoice, product, 3)));

        Invoice otherInvoice = new Invoice();
        otherInvoice.setId(UUID.randomUUID());
        otherInvoice.setInvoiceNumber("INV-102");
        otherInvoice.setCustomer(customer("Rutuja"));
        otherInvoice.setSalesPersonUserId(UUID.randomUUID());
        otherInvoice.setSalesPersonName("Someone Else");
        otherInvoice.setPaymentMode(PaymentMode.CARD);
        otherInvoice.setFinalAmount(BigDecimal.valueOf(999.00).setScale(2));
        otherInvoice.setCreatedAt(LocalDateTime.of(2026, 4, 28, 16, 0));
        otherInvoice.setItems(List.of(invoiceItem(otherInvoice, product, 1)));

        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(otherInvoice, ownInvoice));
        when(customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        StaffUser staff = new StaffUser();
        staff.setId(salespersonId);
        staff.setRole(StaffRole.STAFF);
        staff.setDisplayName("Counter Cashier");

        var response = salespersonSalesService.getSalespersonSales(staff, "WEBSITE", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "MONTHLY");

        assertEquals(true, response.isLockedToCurrentUser());
        assertEquals("Counter Cashier", response.getSalespersonName());
        assertEquals(1, response.getTotalOrders());
        assertEquals(3, response.getTotalItemsSold());
        assertEquals(BigDecimal.valueOf(350.00).setScale(2), response.getTotalSalesAmount());
        assertEquals("INV-101", response.getRecords().get(0).getBillNo());
    }

    private Product product(String name, String category, String sku) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(name);
        product.setCategory(category);
        product.setSku(sku);
        return product;
    }

    private Customer customer(String name) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setName(name);
        customer.setMobile("9999999999");
        return customer;
    }

    private InvoiceItem invoiceItem(Invoice invoice, Product product, int quantity) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(BigDecimal.valueOf(100).setScale(2));
        item.setDiscount(BigDecimal.ZERO.setScale(2));
        return item;
    }

    private OrderItem orderItem(CustomerOrder order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setCategory(product.getCategory());
        item.setSku(product.getSku());
        item.setQuantity(quantity);
        item.setLineTotal(BigDecimal.valueOf(499.00).setScale(2));
        return item;
    }
}
