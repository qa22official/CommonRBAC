package com.example.rbac.api.dto.user;

import java.util.Objects;

public record CreateUserCommand(
        long operatorId,
        String username,
        String nickname,
        String email,
        String phone,
        Integer status,
        String remark,
        String initialPasswordHash) {

    public CreateUserCommand {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(initialPasswordHash, "initialPasswordHash");
    }
}
