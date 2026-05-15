package com.retailshop.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SupportSendProductRequest {
    @NotNull
    private UUID productId;
}
