package com.example.rbac.api.support;

/** 覆盖式授权版本不匹配（并发丢失更新被发现）→ 40950。 */
public class ConflictException extends RuntimeException {
    public ConflictException() { super("version conflict"); }
    public ConflictException(String msg) { super(msg); }
}
