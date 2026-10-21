package com.example.rbac.api.dto.user;

import java.util.Objects;

public record ResetPasswordCommand(
        long operatorId,
        long userId,
        String newPasswordHash) {

    public ResetPasswordCommand {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
    }
}
