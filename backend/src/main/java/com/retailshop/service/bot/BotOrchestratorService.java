package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotInboundMessage;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;

import java.util.List;

public interface BotOrchestratorService {
    BotIntentClassification classify(BotInboundMessage inbound);

    List<BotMemoryRecord> retrieveMemory(BotInboundMessage inbound);

    void remember(BotInboundMessage inbound, String outboundText);
}
