package com.retailshop.service.impl;

import com.retailshop.dto.CartItemResponse;
import com.retailshop.dto.CheckoutQuoteResponse;
import com.retailshop.dto.OrderItemResponse;
import com.retailshop.dto.OrderResponse;
import com.retailshop.dto.PlaceOrderRequest;
import com.retailshop.entity.Address;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.OrderItem;
import com.retailshop.entity.Product;
import com.retailshop.enums.OrderStatus;
import com.retailshop.enums.OrderSource;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.AddressRepository;
import com.retailshop.repository.CustomerOrderRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.service.CartService;
import com.retailshop.service.CheckoutService;
import com.retailshop.service.OrderService;
import com.retailshop.service.PaymentService;
import com.retailshop.service.WhatsAppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final AddressRepository addressRepository;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final WhatsAppMessageService whatsAppMessageService;

    @Override
    @Transactional
    public OrderResponse placeOrder(UUID customerId, PlaceOrderRequest request) {
        String paymentOrderId = firstNonBlank(request.getPhonepeMerchantOrderId(), request.getRazorpayOrderId());
        if (paymentOrderId != null) {
            CustomerOrder existing = orderRepository.findByPaymentOrderId(paymentOrderId).orElse(null);
            if (existing != null) {
                return map(existing);
            }
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Address not found");
        }
        CheckoutQuoteResponse quote = checkoutService.quote(customerId, request.getCouponCode());
        if (quote.getCart().getItems().isEmpty()) {
            throw new BusinessException("Cart is empty");
        }
        if (!paymentService.verifyPayment(request, quote.getFinalTotal())) {
            throw new BusinessException("Payment verification failed");
        }

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber("ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        order.setCustomer(customer);
        order.setAddress(address);
        order.setSubtotal(quote.getSubtotal());
        order.setDiscount(quote.getDiscount());
        order.setTax(quote.getTax());
        order.setDelivery(quote.getDelivery());
        order.setFinalAmount(quote.getFinalTotal());
        order.setCouponCode(quote.getAppliedCouponCode());
        order.setPaymentGateway(resolvePaymentGateway(request));
        order.setPaymentOrderId(paymentOrderId);
        order.setPaymentId(firstNonBlank(request.getPhonepeTransactionId(), request.getRazorpayPaymentId()));
        order.setPaymentStatus("PAID");
        order.setSource(OrderSource.WEBSITE);
        order.setStatus(OrderStatus.CONFIRMED);

        for (CartItemResponse cartItem : quote.getCart().getItems()) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (product.getQuantity() < cartItem.getQuantity()) {
                throw new BusinessException("Stock changed for " + product.getName());
            }
            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setSku(product.getSku());
            orderItem.setCategory(product.getCategory());
            orderItem.setPrice(product.getSellingPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(product.getSellingPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            order.getItems().add(orderItem);
        }
        productRepository.saveAll(order.getItems().stream().map(OrderItem::getProduct).toList());
        CustomerOrder saved = orderRepository.save(order);
        cartService.clearCart(customerId);
        whatsAppMessageService.sendOrderUpdate(saved);
        return map(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(UUID customerId) {
        return orderRepository.findByCustomerIdAndSourceOrderByCreatedAtDesc(customerId, OrderSource.WEBSITE)
                .stream()
                .map(this::map)
                .toList();
    }

    private OrderResponse map(CustomerOrder order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .source(order.getSource())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount())
                .tax(order.getTax())
                .delivery(order.getDelivery())
                .finalAmount(order.getFinalAmount())
                .couponCode(order.getCouponCode())
                .createdAt(order.getCreatedAt())
                .items(order.getItems().stream().map(item -> OrderItemResponse.builder()
                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                        .productName(item.getProductName())
                        .sku(item.getSku())
                        .category(item.getCategory())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build()).toList())
                .build();
    }

    private String resolvePaymentGateway(PlaceOrderRequest request) {
        if (request.getPaymentProvider() != null && !request.getPaymentProvider().isBlank()) {
            return request.getPaymentProvider().trim().toUpperCase();
        }
        if (request.getPhonepeMerchantOrderId() != null && !request.getPhonepeMerchantOrderId().isBlank()) {
            return "PHONEPE";
        }
        if (request.getRazorpayOrderId() != null && !request.getRazorpayOrderId().isBlank()) {
            return "RAZORPAY";
        }
        return "PHONEPE";
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
