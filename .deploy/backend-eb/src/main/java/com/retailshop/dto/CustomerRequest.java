package com.retailshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Mobile must contain 10 to 15 digits")
    private String mobile;
}
