package com.example.rbac.api.dto.user;

import com.example.rbac.api.dto.RoleSummary;

import java.time.Instant;
import java.util.List;

public record UserProfile(
        long id,
        String username,
        String nickname,
        String email,
        String phone,
        int status,
        String remark,
        Instant createdAt,
        List<RoleSummary> roles) {

    public UserProfile {
        roles = List.copyOf(roles);
    }
}
