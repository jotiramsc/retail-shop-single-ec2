package com.retailshop.dto;

import com.retailshop.enums.WhatsAppTemplateKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WhatsAppTemplateSendRequest {
    @NotBlank
    private String mobile;

    @NotNull
    private WhatsAppTemplateKey templateKey;

    private List<String> variables;
}
