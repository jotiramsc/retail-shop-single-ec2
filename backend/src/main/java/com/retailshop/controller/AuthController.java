package com.retailshop.controller;

import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpSendResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;
import com.retailshop.dto.LoginRequest;
import com.retailshop.dto.LoginResponse;
import com.retailshop.entity.StaffUser;
import com.retailshop.service.CustomerAuthService;
import com.retailshop.service.StaffUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
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
    private final StaffUserService staffUserService;
    private final CustomerAuthService customerAuthService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        StaffUser user = staffUserService.getByUsername(request.getUsername());

        return LoginResponse.builder()
                .username(user.getUsername())
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .permissions(staffUserService.getEffectivePermissions(user).stream().map(Enum::name).toList())
                .build();
    }

    @PostMapping("/send-otp")
    public CustomerOtpSendResponse sendOtp(@Valid @RequestBody CustomerOtpRequest request) {
        return customerAuthService.sendOtp(request);
    }

    @PostMapping("/verify-otp")
    public CustomerAuthResponse verifyOtp(@Valid @RequestBody CustomerOtpVerifyRequest request) {
        return customerAuthService.verifyOtp(request);
    }
}
