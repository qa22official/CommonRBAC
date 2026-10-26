package com.example.rbac.auth.internal;

import com.example.rbac.api.security.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt with cost 11 (within the 10–12 range from NFR-3). Plaintext and hashes
 * are never logged.
 */
@Component
public class PasswordHasherImpl implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && encoder.matches(rawPassword, passwordHash);
    }
}
