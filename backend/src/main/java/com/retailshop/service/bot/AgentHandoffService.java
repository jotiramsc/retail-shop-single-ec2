package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotContext;

public interface AgentHandoffService {
    String handoffMessage(String customerMessage, BotContext context);
}
