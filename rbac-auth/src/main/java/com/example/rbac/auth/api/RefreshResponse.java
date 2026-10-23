package com.example.rbac.auth.api;

public record RefreshResponse(String token, long expiresInSeconds, long tokenVersion) {}
