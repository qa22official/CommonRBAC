package com.example.rbac.auth.internal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.rbac.api.support.ClientIpResolver;

/**
 * 客户端 IP 解析。仅信任可信代理网段的 X-Forwarded-For。
 * 默认本实例 + 127.0.0.1 是可信代理；生产用配置覆盖。
 */
@Component
public class ClientIpResolverImpl implements ClientIpResolver {

    private static final Set<String> TRUSTED = new HashSet<>(Arrays.asList("127.0.0.1", "0:0:0:0:0:0:0:1", "::1"));

    @Override
    public String resolve(String remoteAddress, String xffHeader) {
        if (xffHeader != null && !xffHeader.isBlank()) {
            String[] parts = xffHeader.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String cand = parts[i].trim();
                if (!TRUSTED.contains(cand)) return cand;
            }
            return parts[0].trim();
        }
        return remoteAddress;
    }
}
