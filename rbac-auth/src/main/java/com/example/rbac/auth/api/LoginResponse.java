package com.example.rbac.auth.api;

public record LoginResponse(String token, long expiresInSeconds, long tokenVersion, long userId, String nickname) {}
