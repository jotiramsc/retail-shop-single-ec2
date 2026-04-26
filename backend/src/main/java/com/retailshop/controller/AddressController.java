package com.retailshop.controller;

import com.retailshop.dto.AddressRequest;
import com.retailshop.dto.AddressResponse;
import com.retailshop.security.CustomerSecurity;
import com.retailshop.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public AddressResponse addAddress(@Valid @RequestBody AddressRequest request) {
        return addressService.addAddress(CustomerSecurity.currentCustomerId(), request);
    }

    @GetMapping
    public List<AddressResponse> getAddresses() {
        return addressService.getAddresses(CustomerSecurity.currentCustomerId());
    }

    @DeleteMapping("/{id}")
    public void deleteAddress(@PathVariable UUID id) {
        addressService.deleteAddress(CustomerSecurity.currentCustomerId(), id);
    }
}
