package com.retailshop.dto.bot;

import com.retailshop.dto.OmnichannelLeadResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BotInboundMessage {
    private String mobile;
    private String customerName;
    private String messageText;
    private String messageId;
    private String channel;
    private OmnichannelLeadResponse lead;
}
