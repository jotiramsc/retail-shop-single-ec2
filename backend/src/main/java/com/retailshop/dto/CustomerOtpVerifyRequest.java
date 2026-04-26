package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerOtpVerifyRequest {
    @NotBlank
    private String mobile;

    @NotBlank
    private String otp;
}
