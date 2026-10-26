package com.example.rbac.unit;

import com.example.rbac.api.permission.PermissionCodeFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCodeFormatTest {

    @Test
    void acceptsFrameworkAndBusinessShapes() {
        assertThat(PermissionCodeFormat.isValid("*")).isTrue();
        assertThat(PermissionCodeFormat.isValid("sys:user")).isTrue();
        assertThat(PermissionCodeFormat.isValid("sys:user:*")).isTrue();
        assertThat(PermissionCodeFormat.isValid("sys:user:reset-password")).isTrue();
        assertThat(PermissionCodeFormat.isValid("oa:doc:receive.view")).isTrue();
        assertThat(PermissionCodeFormat.isValid("pmis:project:approve")).isTrue();
    }

    @Test
    void rejectsMalformedCodes() {
        assertThat(PermissionCodeFormat.isValid(null)).isFalse();
        assertThat(PermissionCodeFormat.isValid("BadCode")).isFalse();
        assertThat(PermissionCodeFormat.isValid("sys::read")).isFalse();
        assertThat(PermissionCodeFormat.isValid("sys:user:read:extra")).isFalse();
        assertThat(PermissionCodeFormat.isValid("sys user:read")).isFalse();
    }
}
