package com.retailshop.service.impl;

import com.retailshop.dto.StaffUserRequest;
import com.retailshop.dto.StaffUserResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.entity.StaffUser;
import com.retailshop.enums.AppPermission;
import com.retailshop.enums.StaffRole;
import com.retailshop.exception.BusinessException;
import com.retailshop.exception.ResourceNotFoundException;
import com.retailshop.repository.StaffUserRepository;
import com.retailshop.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffUserServiceImpl implements StaffUserService {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<StaffUserResponse> getAllUsers(Pageable pageable) {
        return PaginatedResponse.from(staffUserRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse));
    }

    @Override
    @Transactional
    public StaffUserResponse createUser(StaffUserRequest request) {
        validateRequest(request, true);
        String requestedUsername = request.getUsername().trim();
        if (staffUserRepository.existsByUsername(requestedUsername)) {
            throw new BusinessException("A user with this username already exists");
        }

        StaffUser user = new StaffUser();
        mapRequest(user, request, true);
        return mapToResponse(staffUserRepository.save(user));
    }

    @Override
    @Transactional
    public StaffUserResponse updateUser(UUID id, StaffUserRequest request) {
        validateRequest(request, false);
        StaffUser user = staffUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String requestedUsername = request.getUsername().trim();
        if (!user.getUsername().equals(requestedUsername) && staffUserRepository.existsByUsername(requestedUsername)) {
            throw new BusinessException("A user with this username already exists");
        }

        mapRequest(user, request, false);
        return mapToResponse(staffUserRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public StaffUser getByUsername(String username) {
        return staffUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    public Set<AppPermission> getEffectivePermissions(StaffUser user) {
        if (user.getRole() == StaffRole.ADMIN) {
            return new LinkedHashSet<>(Arrays.asList(AppPermission.values()));
        }
        return new LinkedHashSet<>(user.getPermissions());
    }

    private void validateRequest(StaffUserRequest request, boolean creating) {
        if (creating && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new BusinessException("Password is required when creating a user");
        }
        if (request.getPermissions() == null || request.getPermissions().isEmpty()) {
            throw new BusinessException("Choose at least one menu access permission");
        }
    }

    private void mapRequest(StaffUser user, StaffUserRequest request, boolean creating) {
        user.setUsername(request.getUsername().trim());
        user.setDisplayName(request.getDisplayName().trim());
        user.setRole(request.getRole());
        user.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        user.setPermissions(new LinkedHashSet<>(request.getPermissions()));
        if (creating || (request.getPassword() != null && !request.getPassword().isBlank())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        }
    }

    private StaffUserResponse mapToResponse(StaffUser user) {
        return StaffUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .permissions(getEffectivePermissions(user))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
