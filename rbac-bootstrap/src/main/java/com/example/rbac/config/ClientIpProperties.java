package com.example.rbac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "rbac.client-ip")
public class ClientIpProperties {

    /**
     * Exact addresses or CIDR blocks of proxies allowed to set X-Forwarded-For.
     * Empty means no proxy is trusted and the TCP peer is always used.
     */
    private List<String> trustedProxies = new ArrayList<>();

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }
}
