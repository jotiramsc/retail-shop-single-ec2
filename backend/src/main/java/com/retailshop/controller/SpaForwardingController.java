package com.retailshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardingController {

    @GetMapping({
            "/",
            "/login",
            "/customer-login",
            "/products",
            "/cart",
            "/checkout",
            "/orders",
            "/account",
            "/app",
            "/app/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
