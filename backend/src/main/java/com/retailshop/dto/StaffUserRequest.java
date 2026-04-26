package com.retailshop.dto;

import com.retailshop.enums.AppPermission;
import com.retailshop.enums.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class StaffUserRequest {

    @NotBlank
    private String username;

    private String password;

    @NotBlank
    private String displayName;

    @NotNull
    private StaffRole role;

    @NotNull
    private Boolean enabled;

    @NotEmpty
    private Set<AppPermission> permissions = new LinkedHashSet<>();
}
