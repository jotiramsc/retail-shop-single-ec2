package com.retailshop.service.impl;

import com.retailshop.service.MarketingAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingScheduler {

    private final MarketingAutomationService marketingAutomationService;

    @Scheduled(fixedDelay = 60_000)
    public void publishScheduledMarketingContent() {
        try {
            marketingAutomationService.publishScheduled();
        } catch (Exception exception) {
            log.warn("Scheduled marketing publish run failed", exception);
        }
    }
}
