package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotContext;
import com.retailshop.dto.bot.BotIntentClassification;
import com.retailshop.dto.bot.BotMemoryRecord;

import java.util.List;
import java.util.Optional;

public interface BotOpenAiService {
    Optional<BotIntentClassification> classifyIntent(String message, BotContext context, List<BotMemoryRecord> memories);

    Optional<String> generateReply(String message, BotIntentClassification intent, BotContext context, List<BotMemoryRecord> memories, String factualDraft);

    Optional<String> summarizeMemory(String message, String reply, BotContext context);

    Optional<List<Double>> embed(String text);
}
