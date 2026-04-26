package com.retailshop.controller;

import com.retailshop.config.SecurityProperties;
import com.retailshop.dto.LoginRequest;
import com.retailshop.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityProperties securityProperties;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityProperties.UserConfig user = securityProperties.getUsers()
                .stream()
                .filter(item -> item.getUsername().equals(request.getUsername()))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        return LoginResponse.builder()
                .username(user.getUsername())
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .build();
    }
}
