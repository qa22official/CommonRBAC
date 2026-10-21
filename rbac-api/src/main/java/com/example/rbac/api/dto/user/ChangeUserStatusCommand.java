package com.example.rbac.api.dto.user;

public record ChangeUserStatusCommand(
        long operatorId,
        long userId,
        int status) {
}
