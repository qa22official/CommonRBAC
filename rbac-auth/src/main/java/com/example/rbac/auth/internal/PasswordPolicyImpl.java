package com.example.rbac.auth.internal;

import com.example.rbac.api.security.PasswordPolicy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * NFR-3: at least 8 characters, upper case, lower case and a digit, and not
 * equal to the account name. BCrypt only consumes the first 72 bytes, so longer
 * inputs are rejected rather than silently truncated.
 */
@Component
public class PasswordPolicyImpl implements PasswordPolicy {

    @Override
    public Optional<String> violation(String username, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return Optional.of("密码不能为空");
        }
        if (rawPassword.length() < 8) {
            return Optional.of("密码需至少 8 位，且包含大小写字母与数字");
        }
        if (rawPassword.length() > 72) {
            return Optional.of("密码长度不能超过 72 位");
        }
        boolean upper = rawPassword.chars().anyMatch(Character::isUpperCase);
        boolean lower = rawPassword.chars().anyMatch(Character::isLowerCase);
        boolean digit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!upper || !lower || !digit) {
            return Optional.of("密码需至少 8 位，且包含大小写字母与数字");
        }
        if (username != null && username.equalsIgnoreCase(rawPassword)) {
            return Optional.of("密码不能与账号相同");
        }
        return Optional.empty();
    }
}
