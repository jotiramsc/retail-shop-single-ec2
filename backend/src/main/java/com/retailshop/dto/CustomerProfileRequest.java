package com.retailshop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CustomerProfileRequest {

    @Size(max = 255)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String mobile;

    private LocalDate dateOfBirth;

    @Size(max = 40)
    private String gender;

    @Size(max = 1000)
    private String profileImageUrl;

    @Size(max = 20)
    private String alternateMobile;
}
