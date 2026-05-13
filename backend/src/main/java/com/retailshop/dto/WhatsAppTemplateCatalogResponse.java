package com.retailshop.dto;

import com.retailshop.enums.WhatsAppTemplateKey;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WhatsAppTemplateCatalogResponse {
    private WhatsAppTemplateKey key;
    private String templateName;
    private String languageCode;
    private List<String> variables;
}
