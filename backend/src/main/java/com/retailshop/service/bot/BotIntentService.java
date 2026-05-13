package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;

import java.util.List;

public interface BotIntentService {
    BotIntentClassification classify(String message, BotContext context, List<BotMemoryRecord> memories);
}
