package com.example.rbac.unit;

import com.example.rbac.api.permission.PermissionMatcher;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMatcherTest {

    @Test
    void exactMatchWins() {
        assertThat(PermissionMatcher.matches(Set.of("sys:user:read"), "sys:user:read")).isTrue();
    }

    @Test
    void allWildcardMatchesEverything() {
        assertThat(PermissionMatcher.matches(Set.of("*"), "oa:doc:receive.approve")).isTrue();
    }

    @Test
    void moduleWildcardMatchesDescendants() {
        assertThat(PermissionMatcher.matches(Set.of("sys:user:*"), "sys:user:delete")).isTrue();
        assertThat(PermissionMatcher.matches(Set.of("sys:user:*"), "sys:role:read")).isFalse();
    }

    @Test
    void wildcardIsNotMatchedWithoutWildcardEntry() {
        assertThat(PermissionMatcher.matches(Set.of("sys:user:read"), "sys:user:delete")).isFalse();
    }

    @Test
    void prefixMustEndAtSeparator() {
        assertThat(PermissionMatcher.matches(Set.of("sys:user:*"), "sys:username:read")).isFalse();
    }
}
