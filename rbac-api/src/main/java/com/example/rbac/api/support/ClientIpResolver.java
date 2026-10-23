package com.example.rbac.api.support;

/** 客户端 IP 解析接口（rbac-api 保持零 Servlet 依赖，参数为纯 String）。 */
public interface ClientIpResolver {

    /** @param remoteAddress HttpServletRequest.getRemoteAddr()
     *  @param xffHeader      X-Forwarded-For 原始值，null/空视为不存在 */
    String resolve(String remoteAddress, String xffHeader);
}
