package com.example.rbac.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Soft TTL is a refresh hint, never an eviction trigger (02 §11.1). Changing it
 * must be mirrored in 01 FR-A9's timing commitments.
 */
@Component
@ConfigurationProperties(prefix = "rbac.cache")
public class CacheProperties {

    private int softTtlSeconds = 30;

    private String stalePolicy = "fail-open";

    private boolean loadOnStartup = true;

    public int getSoftTtlSeconds() {
        return softTtlSeconds;
    }

    public void setSoftTtlSeconds(int softTtlSeconds) {
        this.softTtlSeconds = softTtlSeconds;
    }

    public String getStalePolicy() {
        return stalePolicy;
    }

    public void setStalePolicy(String stalePolicy) {
        this.stalePolicy = stalePolicy;
    }

    public boolean isLoadOnStartup() {
        return loadOnStartup;
    }

    public void setLoadOnStartup(boolean loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    public boolean failClosed() {
        return "fail-closed".equalsIgnoreCase(stalePolicy);
    }
}
