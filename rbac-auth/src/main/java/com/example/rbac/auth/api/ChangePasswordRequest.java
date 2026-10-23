package com.example.rbac.auth.api;

public record ChangePasswordRequest(String oldPassword, String newPassword) {}
