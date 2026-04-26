package com.retailshop.service;

import com.retailshop.entity.Customer;

public interface WhatsAppService {
    boolean sendMessage(Customer customer, String content);
    boolean broadcast(String content);
}
