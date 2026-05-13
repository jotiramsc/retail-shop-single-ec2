package com.retailshop.service.bot.impl;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.service.bot.AgentHandoffService;
import org.springframework.stereotype.Service;

@Service
public class AgentHandoffServiceImpl implements AgentHandoffService {

    @Override
    public String handoffMessage(String customerMessage, BotContext context) {
        String latestOrder = "";
        if (context != null && context.getRecentOrders() != null && !context.getRecentOrders().isEmpty()) {
            CustomerOrder order = context.getRecentOrders().get(0);
            latestOrder = "\nLatest order I found: " + safe(order.getOrderNumber()) + " (" + safe(order.getStatus() == null ? null : order.getStatus().name()) + ")";
        }
        return "I am connecting you to our support team now. I will keep your latest message ready for them." + latestOrder
                + "\n\nYou can also send your order number, payment screenshot, or product photo here.";
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }
}
