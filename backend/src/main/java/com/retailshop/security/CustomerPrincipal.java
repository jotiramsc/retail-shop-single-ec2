package com.retailshop.security;

import java.util.UUID;

public record CustomerPrincipal(UUID customerId, String mobile) {
}
