package com.retailshop.service.impl;

import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Invoice;
import com.retailshop.entity.InvoiceItem;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderSource;
import com.retailshop.enums.OrderStatus;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.InvoiceRepository;
import com.retailshop.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    void shouldGenerateMonthlySalesReportForCategoryAcrossBillingAndWebsiteSales() {
        Product jewelleryProduct = new Product();
        jewelleryProduct.setId(UUID.randomUUID());
        jewelleryProduct.setName("Temple Necklace");
        jewelleryProduct.setCategory("JEWELLERY");
        jewelleryProduct.setSku("JEW-100");

        Product cosmeticsProduct = new Product();
        cosmeticsProduct.setId(UUID.randomUUID());
        cosmeticsProduct.setName("Rose Serum");
        cosmeticsProduct.setCategory("COSMETICS");
        cosmeticsProduct.setSku("COS-200");

        Invoice invoice = new Invoice();
        invoice.setDiscount(BigDecimal.valueOf(20).setScale(2));
        invoice.setCustomer(customer("Anika", "9876543210"));
        invoice.setCreatedAt(LocalDateTime.of(2026, 4, 10, 12, 0));
        invoice.setItems(List.of(
                invoiceItem(invoice, jewelleryProduct, 2, 100, 10),
                invoiceItem(invoice, cosmeticsProduct, 1, 50, 0)
        ));

        CustomerOrder websiteOrder = new CustomerOrder();
        websiteOrder.setSource(OrderSource.WEBSITE);
        websiteOrder.setStatus(OrderStatus.COMPLETED);
        websiteOrder.setCustomer(customer("Riya", "9988776655"));
        websiteOrder.setDiscount(BigDecimal.valueOf(15).setScale(2));
        websiteOrder.setCreatedAt(LocalDateTime.of(2026, 4, 18, 16, 30));
        websiteOrder.setItems(List.of(
                orderItem(websiteOrder, jewelleryProduct, 1, 120),
                orderItem(websiteOrder, cosmeticsProduct, 1, 80)
        ));

        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(invoice));
        when(customerOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(websiteOrder));

        var response = reportService.getSalesReport("MONTHLY", "2026-04", null, "CATEGORY", "JEWELLERY", null);

        assertEquals("MONTHLY", response.getPeriod());
        assertEquals("CATEGORY", response.getScope());
        assertEquals(2, response.getOrderCount());
        assertEquals(3, response.getQuantitySold());
        assertEquals(BigDecimal.valueOf(320.00).setScale(2), response.getGrossSales());
        assertEquals(BigDecimal.valueOf(26.92).setScale(2), response.getDiscount());
        assertEquals(BigDecimal.valueOf(293.08).setScale(2), response.getNetSales());
        assertEquals(1, response.getRows().size());
        assertEquals("Temple Necklace", response.getRows().get(0).getProductName());
    }

    private Customer customer(String name, String mobile) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setName(name);
        customer.setMobile(mobile);
        return customer;
    }

    private InvoiceItem invoiceItem(Invoice invoice, Product product, int quantity, double unitPrice, double discount) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(BigDecimal.valueOf(unitPrice).setScale(2));
        item.setDiscount(BigDecimal.valueOf(discount).setScale(2));
        return item;
    }

    private OrderItem orderItem(CustomerOrder order, Product product, int quantity, double lineTotal) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setCategory(product.getCategory());
        item.setSku(product.getSku());
        item.setQuantity(quantity);
        item.setLineTotal(BigDecimal.valueOf(lineTotal).setScale(2));
        return item;
    }
}
