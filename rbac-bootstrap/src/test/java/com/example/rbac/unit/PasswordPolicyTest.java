package com.example.rbac.unit;

import com.example.rbac.auth.internal.PasswordPolicyImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicyImpl policy = new PasswordPolicyImpl();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.violation("alice", "Alice@12345")).isEmpty();
    }

    @Test
    void rejectsWeakPasswords() {
        assertThat(policy.violation("alice", "short1A")).isPresent();
        assertThat(policy.violation("alice", "alllowercase1")).isPresent();
        assertThat(policy.violation("alice", "ALLUPPERCASE1")).isPresent();
        assertThat(policy.violation("alice", "NoDigitsHere")).isPresent();
    }

    @Test
    void rejectsPasswordEqualToUsername() {
        assertThat(policy.violation("Alice@12345", "Alice@12345")).isPresent();
    }
}
