package com.retailshop.service.impl;

import com.retailshop.entity.Customer;
import com.retailshop.service.WhatsAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WhatsAppServiceImpl implements WhatsAppService {

    @Override
    public boolean sendMessage(Customer customer, String content) {
        log.info("Mock WhatsApp sent to {} ({}): {}", customer.getName(), customer.getMobile(), content);
        return true;
    }

    @Override
    public boolean broadcast(String content) {
        log.info("Mock WhatsApp broadcast triggered: {}", content);
        return true;
    }
}
