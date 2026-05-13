package com.retailshop.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsAppTemplateSendResponse {
    private boolean success;
    private String responseId;
    private String errorMessage;
}
