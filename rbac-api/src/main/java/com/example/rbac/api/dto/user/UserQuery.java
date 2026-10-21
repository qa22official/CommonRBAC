package com.example.rbac.api.dto.user;

public record UserQuery(
        int page,
        int size,
        String keyword,
        Integer status) {
}
