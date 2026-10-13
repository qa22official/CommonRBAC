package com.example.rbac.api.support;

public interface ClientIpResolver {

    String resolve(String remoteAddress, String forwardedFor);
}
