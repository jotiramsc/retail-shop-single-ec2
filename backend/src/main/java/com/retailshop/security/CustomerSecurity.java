package com.retailshop.security;

import com.retailshop.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CustomerSecurity {
    private CustomerSecurity() {
    }

    public static UUID currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new BusinessException("Customer login is required");
        }
        return principal.customerId();
    }
}
