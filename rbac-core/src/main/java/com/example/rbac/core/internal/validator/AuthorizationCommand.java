package com.example.rbac.core.internal.validator;

import java.util.Set;

/**
 * Intent of one authorization change. Validators receive the final collection
 * (not the delta) so each rule can decide whether the resulting state is legal;
 * see 05 §5.4. This type is internal to {@code rbac-core} and must not leak into
 * {@code rbac-api}.
 */
public record AuthorizationCommand(
        long operatorId,
        TargetType targetType,
        long targetUserId,
        long targetRoleId,
        Set<Long> finalRoleIds,
        Set<String> finalPermissionCodes,
        Integer targetStatus) {

    public AuthorizationCommand {
        finalRoleIds = finalRoleIds == null ? Set.of() : Set.copyOf(finalRoleIds);
        finalPermissionCodes = finalPermissionCodes == null ? Set.of() : Set.copyOf(finalPermissionCodes);
    }

    public enum TargetType {
        USER_ROLES,
        ROLE_PERMISSIONS,
        USER_STATUS,
        ROLE_STATUS,
        ROLE_DELETE,
        PERMISSION_DELETE
    }
}
