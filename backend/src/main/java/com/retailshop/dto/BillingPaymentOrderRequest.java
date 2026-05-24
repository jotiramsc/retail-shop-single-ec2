package com.retailshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingPaymentOrderRequest {
    @Valid
    @NotNull
    private InvoiceCreateRequest invoice;

    private String redirectUrl;
}
