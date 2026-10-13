package com.example.rbac.api.identity;

public record CurrentUser(long userId, long tokenVersion) {
}
