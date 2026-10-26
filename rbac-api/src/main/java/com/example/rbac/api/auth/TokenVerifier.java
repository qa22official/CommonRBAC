package com.example.rbac.api.auth;

import com.example.rbac.api.support.BusinessException;

/**
 * Verifies a compact JWT. Implementations throw
 * {@link BusinessException} with code {@code 40100} for any signature,
 * structure, issuer, audience, or expiry failure. An unknown {@code kid} after a
 * key rotation is also 40100 (global revocation, ADR-23).
 */
public interface TokenVerifier {

    VerifiedToken verify(String token);
}
