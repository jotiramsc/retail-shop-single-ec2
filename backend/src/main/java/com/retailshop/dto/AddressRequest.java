package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddressRequest {
    private String label;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String mobile;

    @NotBlank
    private String line1;

    private String line2;
    private String landmark;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String pincode;

    private BigDecimal latitude;
    private BigDecimal longitude;
}
