package com.retailshop.dto;

import com.retailshop.enums.DiscountType;
import com.retailshop.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class InvoiceCreateRequest {

    @NotBlank
    private String customerName;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Mobile must contain 10 to 15 digits")
    private String customerMobile;

    @Valid
    @NotEmpty
    private List<InvoiceItemRequest> items;

    @NotNull
    private PaymentMode paymentMode;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal manualDiscount;

    @NotNull
    private DiscountType manualDiscountType;
}
