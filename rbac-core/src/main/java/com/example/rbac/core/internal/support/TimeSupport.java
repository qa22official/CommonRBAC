package com.example.rbac.core.internal.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * All RBAC timestamps are stored as UTC {@code DATETIME(3)} values. Keeping the
 * persistence type as {@link LocalDateTime} avoids implicit JVM time-zone
 * conversions in both MySQL and H2.
 */
public final class TimeSupport {

    private TimeSupport() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime fromInstant(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
