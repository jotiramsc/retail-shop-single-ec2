package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerOtpRequest {
    @NotBlank
    private String mobile;
}
