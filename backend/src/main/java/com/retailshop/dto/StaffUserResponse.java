package com.retailshop.dto;

import com.retailshop.enums.AppPermission;
import com.retailshop.enums.StaffRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class StaffUserResponse {
    private UUID id;
    private String username;
    private String displayName;
    private StaffRole role;
    private Boolean enabled;
    private Set<AppPermission> permissions;
    private LocalDateTime createdAt;
}
