package com.retailshop.service;

import com.retailshop.entity.Campaign;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;
import com.retailshop.enums.WhatsAppTemplateKey;

import java.util.List;

public interface WhatsAppMessageService {
    boolean isConfigured();

    MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes);

    MarketingChannelResult sendTemplate(String mobile, WhatsAppTemplateKey templateKey, List<String> variables);

    MarketingChannelResult sendText(String mobile, String body);

    MarketingChannelResult sendImage(String mobile, String imageUrl, String caption);

    MarketingChannelResult sendOrderConfirmation(CustomerOrder order);

    MarketingChannelResult sendOrderDispatched(CustomerOrder order, String trackingId, String trackingUrl);

    MarketingChannelResult sendOrderDelivered(CustomerOrder order);

    MarketingChannelResult sendOrderCancelled(CustomerOrder order);

    MarketingChannelResult sendOrderReturned(CustomerOrder order);

    MarketingChannelResult sendRefundInitiated(CustomerOrder order, String refundAmount);

    MarketingChannelResult sendPaymentSuccess(CustomerOrder order);

    MarketingChannelResult sendPaymentFailed(CustomerOrder order);

    MarketingChannelResult sendBotWelcome(String mobile, String customerName);

    MarketingChannelResult sendBotMenu(String mobile, String customerName);

    MarketingChannelResult sendSupportEscalation(String mobile, String customerName);

    MarketingChannelResult sendOutOfOffice(String mobile, String customerName);

    MarketingChannelResult sendFeedbackRequest(CustomerOrder order);

    MarketingChannelResult sendOrderUpdate(CustomerOrder order);

    MarketingChannelResult broadcastOffer(List<Customer> customers, String content);

    MarketingChannelResult publishCampaign(Campaign campaign, List<Customer> customers);
}
