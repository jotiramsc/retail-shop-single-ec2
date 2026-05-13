package com.retailshop.dto.bot;

import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Offer;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class BotContext {
    private Customer customer;
    private String mobile;
    private List<CustomerOrder> recentOrders;
    private int orderCount;
    private BigDecimal totalOrderValue;
    private List<Offer> activeOffers;
    private List<String> categories;
}
