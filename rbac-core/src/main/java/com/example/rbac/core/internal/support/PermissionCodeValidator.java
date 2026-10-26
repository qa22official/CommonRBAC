package com.example.rbac.core.internal.support;

import com.example.rbac.api.permission.PermissionCodeFormat;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;

/**
 * Throwing adapter around the shared {@link PermissionCodeFormat}.
 */
public final class PermissionCodeValidator {

    private PermissionCodeValidator() {
    }

    public static boolean isValid(String code) {
        return PermissionCodeFormat.isValid(code);
    }

    public static void requireValid(String code) {
        if (!isValid(code)) {
            throw new BusinessException(ErrorCodes.PERMISSION_CODE_INVALID,
                    "权限码格式非法，应为 <模块>:<资源>:<操作>");
        }
    }
}
