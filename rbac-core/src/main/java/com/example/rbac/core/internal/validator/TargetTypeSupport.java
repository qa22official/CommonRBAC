package com.example.rbac.core.internal.validator;

import java.util.Objects;

final class TargetTypeSupport {

    private TargetTypeSupport() {
    }

    static void require(AuthorizationCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.targetType(), "targetType");
    }
}
