package com.example.rbac.api.event;

public record TokenRevokedEvent(long userId, long tokenVersion) {
}
