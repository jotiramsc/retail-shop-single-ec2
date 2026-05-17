package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerOtpVerifyRequest {
    @NotBlank
    private String mobile;

    @NotBlank
    @Pattern(regexp = "\\d{4,8}", message = "Enter a valid numeric OTP")
    private String otp;

    private String purpose;

    private String customerId;
}
