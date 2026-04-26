package com.retailshop.service;

import com.retailshop.dto.StaffUserRequest;
import com.retailshop.dto.StaffUserResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.AppPermission;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StaffUserService {
    PaginatedResponse<StaffUserResponse> getAllUsers(Pageable pageable);
    StaffUserResponse createUser(StaffUserRequest request);
    StaffUserResponse updateUser(UUID id, StaffUserRequest request);
    StaffUser getByUsername(String username);
    Set<AppPermission> getEffectivePermissions(StaffUser user);
}
