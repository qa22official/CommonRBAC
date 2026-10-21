package com.example.rbac.api.dto.user;

import java.time.Instant;

public record UserListItem(
        long id,
        String username,
        String nickname,
        String email,
        String phone,
        int status,
        Instant createdAt) {
}
