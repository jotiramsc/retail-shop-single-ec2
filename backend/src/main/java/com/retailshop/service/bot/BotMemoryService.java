package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotMemoryRecord;

import java.util.List;

public interface BotMemoryService {
    List<BotMemoryRecord> retrieve(String mobile, String query, int limit);

    void rememberInteraction(String mobile, String inboundText, String outboundText, BotContext context);
}
