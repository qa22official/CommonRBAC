package com.example.rbac.auth.internal;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码策略：使用 Spring Security 自带的 BCryptPasswordEncoder（强度 10）。
 * 策略：长度 ≥ 8、含大小写与数字、不得与账号相同。
 */
public final class PasswordPolicy {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);
    private static final int MIN_LEN = 8;

    private PasswordPolicy() {
    }

    public static String hash(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hash) {
        try { return ENCODER.matches(raw, hash); }
        catch (Exception e) { return false; }
    }

    /** 返回 null = 合法；否则失败描述。 */
    public static String validate(String username, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_LEN) return "密码长度至少 8 位";
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (char c : newPassword.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!(hasUpper && hasLower && hasDigit)) return "密码必须同时包含大写字母、小写字母与数字";
        if (username != null && newPassword.equalsIgnoreCase(username)) return "密码不得与账号相同";
        return null;
    }
}
