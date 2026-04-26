package com.retailshop.service;

import com.retailshop.entity.Campaign;
import com.retailshop.entity.Customer;
import com.retailshop.entity.CustomerOrder;

import java.util.List;

public interface WhatsAppMessageService {
    boolean isConfigured();

    MarketingChannelResult sendOtp(String mobile, String otp, long otpTtlMinutes);

    MarketingChannelResult sendOrderUpdate(CustomerOrder order);

    MarketingChannelResult broadcastOffer(List<Customer> customers, String content);

    MarketingChannelResult publishCampaign(Campaign campaign, List<Customer> customers);
}
