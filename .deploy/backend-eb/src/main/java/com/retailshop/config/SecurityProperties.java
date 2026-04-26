package com.retailshop.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @Valid
    @NotEmpty
    private List<UserConfig> users = new ArrayList<>();

    @Getter
    @Setter
    public static class UserConfig {
        @NotBlank
        private String username;

        @NotBlank
        private String password;

        @NotBlank
        private String role;

        @NotBlank
        private String displayName;
    }
}
