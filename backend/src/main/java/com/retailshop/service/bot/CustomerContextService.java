package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotContext;

public interface CustomerContextService {
    BotContext buildContext(String mobile);
}
