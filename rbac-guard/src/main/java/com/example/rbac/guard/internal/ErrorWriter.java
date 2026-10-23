package com.example.rbac.guard.internal;

import java.io.IOException;

/** 写 HTTP 错误响应。rbac-guard 层的专用接口——不放 rbac-api（rbac-api 保持零 Spring 依赖）。 */
public interface ErrorWriter {
    void write(jakarta.servlet.http.HttpServletResponse response, int code, String message) throws IOException;
}
