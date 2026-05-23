package com.retailshop.controller;

import com.retailshop.dto.StaffUserRequest;
import com.retailshop.dto.StaffUserResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.dto.SalesPersonOptionResponse;
import com.retailshop.service.StaffUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class StaffUserController {

    private final StaffUserService staffUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PaginatedResponse<StaffUserResponse> getUsers(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return staffUserService.getAllUsers(pageable);
    }

    @GetMapping("/salespeople")
    @PreAuthorize("hasAnyAuthority('PERM_BILLING', 'PERM_BILLING_CHECKOUT', 'PERM_REPORTS', 'PERM_USER_MANAGEMENT')")
    public List<SalesPersonOptionResponse> getSalesPeople() {
        return staffUserService.getActiveSalesPeople();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffUserResponse createUser(@Valid @RequestBody StaffUserRequest request) {
        return staffUserService.createUser(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffUserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody StaffUserRequest request) {
        return staffUserService.updateUser(id, request);
    }
}
