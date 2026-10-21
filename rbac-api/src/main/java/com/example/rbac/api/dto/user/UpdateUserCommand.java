package com.example.rbac.api.dto.user;

public record UpdateUserCommand(
        long operatorId,
        long userId,
        String nickname,
        String email,
        String phone,
        String remark) {
}
