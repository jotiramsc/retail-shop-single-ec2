package com.retailshop.service.bot;

import com.retailshop.dto.OmnichannelProductCardResponse;
import com.retailshop.dto.bot.BotDeliveryTimeline;
import com.retailshop.dto.bot.BotOrderCard;
import com.retailshop.dto.bot.BotPaymentSummary;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.entity.Product;

import java.util.List;

public interface BotResponseComposerService {
    String productIntro(List<OmnichannelProductCardResponse> products);

    String productCaption(OmnichannelProductCardResponse product);

    BotOrderCard orderCard(CustomerOrder order);

    BotDeliveryTimeline deliveryTimeline(CustomerOrder order);

    BotPaymentSummary paymentSummary(CustomerOrder order);

    OmnichannelProductCardResponse productCard(Product product);
}
