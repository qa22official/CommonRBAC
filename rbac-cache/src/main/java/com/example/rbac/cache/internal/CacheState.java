package com.example.rbac.cache.internal;

/** 缓存条目状态机。 */
public enum CacheState {
    VALID,
    STALE,
    RELOADING,
    FAILED
}
