package com.retailshop.service;

import com.retailshop.dto.AddressRequest;
import com.retailshop.dto.AddressResponse;

import java.util.List;
import java.util.UUID;

public interface AddressService {
    AddressResponse addAddress(UUID customerId, AddressRequest request);

    List<AddressResponse> getAddresses(UUID customerId);

    void deleteAddress(UUID customerId, UUID addressId);
}
