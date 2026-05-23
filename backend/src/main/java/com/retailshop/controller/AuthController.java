package com.retailshop.controller;

import com.retailshop.dto.CustomerAuthResponse;
import com.retailshop.dto.CustomerGoogleLoginRequest;
import com.retailshop.dto.CustomerOtpRequest;
import com.retailshop.dto.CustomerOtpSendResponse;
import com.retailshop.dto.CustomerOtpVerifyRequest;
import com.retailshop.dto.LoginRequest;
import com.retailshop.dto.LoginResponse;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.AppPermission;
import com.retailshop.enums.StaffRole;
import com.retailshop.security.StaffJwtService;
import com.retailshop.service.CustomerAuthService;
import com.retailshop.service.CustomerService;
import com.retailshop.service.StaffUserService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final CustomerService customerService;
    private final StaffJwtService staffJwtService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        StaffUser user = staffUserService.getByUsername(request.getUsername());
        StaffJwtService.StaffToken token = staffJwtService.issueToken(user);

        return LoginResponse.builder()
                .username(user.getUsername())
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .permissions((user.getRole() == StaffRole.ADMIN || user.getRole() == StaffRole.OWNER
                        ? java.util.Arrays.stream(AppPermission.values())
                        : staffUserService.getEffectivePermissions(user).stream()).map(Enum::name).toList())
                .token("Bearer " + token.token())
                .expiresAt(token.expiresAt())
                .build();
    }

    @PostMapping("/send-otp")
    public CustomerOtpSendResponse sendOtp(@Valid @RequestBody CustomerOtpRequest request) {
        return customerAuthService.sendOtp(request);
    }

    @PostMapping("/verify-otp")
    public CustomerAuthResponse verifyOtp(@Valid @RequestBody CustomerOtpVerifyRequest request, HttpServletRequest httpRequest) {
        CustomerAuthResponse response = customerAuthService.verifyOtp(request);
        recordCustomerLogin(response, "SIGNUP".equalsIgnoreCase(request.getPurpose()) ? "Mobile OTP Signup" : "Mobile OTP", httpRequest);
        return response;
    }

    @PostMapping("/google/verify-mobile")
    public CustomerAuthResponse verifyGoogleMobileOtp(@Valid @RequestBody CustomerOtpVerifyRequest request, HttpServletRequest httpRequest) {
        CustomerAuthResponse response = customerAuthService.verifyGoogleMobileOtp(request);
        recordCustomerLogin(response, "Google + Mobile OTP", httpRequest);
        return response;
    }

    @PostMapping("/google")
    public CustomerAuthResponse googleLogin(@Valid @RequestBody CustomerGoogleLoginRequest request, HttpServletRequest httpRequest) {
        CustomerAuthResponse response = customerAuthService.loginWithGoogle(request);
        if (response != null && response.getToken() != null && !response.isRequiresMobileOtp()) {
            recordCustomerLogin(response, "Google login", httpRequest);
        }
        return response;
    }

    private void recordCustomerLogin(CustomerAuthResponse response, String method, HttpServletRequest request) {
        if (response != null && response.getCustomerId() != null && response.getToken() != null) {
            customerService.recordLogin(response.getCustomerId(), method, "SUCCESS", request);
        }
    }
}
