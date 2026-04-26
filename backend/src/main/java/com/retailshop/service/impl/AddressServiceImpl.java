package com.retailshop.service.impl;

import com.retailshop.dto.AddressRequest;
import com.retailshop.dto.AddressResponse;
import com.retailshop.entity.Address;
import com.retailshop.entity.Customer;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.AddressRepository;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public AddressResponse addAddress(UUID customerId, AddressRequest request) {
        validatePune(request);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Address address = new Address();
        address.setCustomer(customer);
        address.setLabel(request.getLabel());
        address.setRecipientName(request.getRecipientName());
        address.setMobile(request.getMobile());
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setLandmark(request.getLandmark());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(request.getPincode());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        return map(addressRepository.save(address));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID customerId) {
        return addressRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Address not found");
        }
        addressRepository.delete(address);
    }

    private void validatePune(AddressRequest request) {
        String city = String.valueOf(request.getCity()).toLowerCase(Locale.ROOT);
        String pincode = String.valueOf(request.getPincode());
        if (!city.contains("pune") && !pincode.startsWith("411")) {
            throw new BusinessException("Delivery is currently available only in Pune");
        }
    }

    private AddressResponse map(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .recipientName(address.getRecipientName())
                .mobile(address.getMobile())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .landmark(address.getLandmark())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .build();
    }
}
