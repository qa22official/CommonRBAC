package com.example.rbac.api.dto.role;

public record RoleQuery(
        int page,
        int size,
        String keyword,
        Integer status) {
}
