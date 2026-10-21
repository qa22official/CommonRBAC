package com.example.rbac.api.security;

/**
 * Password hashing contract. Implementations use BCrypt and must never log
 * plaintext passwords or password hashes.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
