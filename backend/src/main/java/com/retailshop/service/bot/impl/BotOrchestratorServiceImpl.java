package com.retailshop.service.bot.impl;

import com.retailshop.config.BotProperties;
import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotInboundMessage;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;
import com.retailshop.service.bot.BotIntentService;
import com.retailshop.service.bot.BotMemoryService;
import com.retailshop.service.bot.BotOrchestratorService;
import com.retailshop.service.bot.CustomerContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotOrchestratorServiceImpl implements BotOrchestratorService {

    private final BotProperties properties;
    private final CustomerContextService customerContextService;
    private final BotMemoryService memoryService;
    private final BotIntentService intentService;

    @Override
    public BotIntentClassification classify(BotInboundMessage inbound) {
        if (!properties.isEnabled() || inbound == null) {
            return BotIntentClassification.fallback(inbound == null ? "" : inbound.getMessageText());
        }
        try {
            BotContext context = customerContextService.buildContext(inbound.getMobile());
            List<BotMemoryRecord> memories = memoryService.retrieve(inbound.getMobile(), inbound.getMessageText(), properties.getMemoryTopK());
            return intentService.classify(inbound.getMessageText(), context, memories);
        } catch (Exception exception) {
            log.debug("Bot orchestration classification failed", exception);
            return BotIntentClassification.fallback(inbound.getMessageText());
        }
    }

    @Override
    public List<BotMemoryRecord> retrieveMemory(BotInboundMessage inbound) {
        if (!properties.isEnabled() || inbound == null) {
            return List.of();
        }
        return memoryService.retrieve(inbound.getMobile(), inbound.getMessageText(), properties.getMemoryTopK());
    }

    @Override
    public String polishReply(BotInboundMessage inbound, BotIntentClassification classification, String factualDraft) {
        if (!properties.isEnabled() || inbound == null || factualDraft == null || factualDraft.isBlank()) {
            return factualDraft;
        }
        try {
            BotContext context = customerContextService.buildContext(inbound.getMobile());
            List<BotMemoryRecord> memories = memoryService.retrieve(inbound.getMobile(), inbound.getMessageText(), properties.getMemoryTopK());
            return intentService.polishReply(inbound.getMessageText(), classification, context, memories, factualDraft)
                    .orElse(factualDraft);
        } catch (Exception exception) {
            log.debug("Bot orchestration reply polishing failed", exception);
            return factualDraft;
        }
    }

    @Override
    public void remember(BotInboundMessage inbound, String outboundText) {
        if (!properties.isEnabled() || inbound == null) {
            return;
        }
        try {
            BotContext context = customerContextService.buildContext(inbound.getMobile());
            memoryService.rememberInteraction(inbound.getMobile(), inbound.getMessageText(), outboundText, context);
        } catch (Exception exception) {
            log.debug("Bot orchestration memory write failed", exception);
        }
    }
}
