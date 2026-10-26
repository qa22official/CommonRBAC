package com.example.rbac.unit;

import com.example.rbac.config.ClientIpProperties;
import com.example.rbac.config.ClientIpResolverImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolverImpl resolver(String... trusted) {
        ClientIpProperties properties = new ClientIpProperties();
        properties.setTrustedProxies(List.of(trusted));
        return new ClientIpResolverImpl(properties, new SimpleMeterRegistry());
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        assertThat(resolver("10.0.0.1").resolve("8.8.8.8", "1.2.3.4")).isEqualTo("8.8.8.8");
    }

    @Test
    void walksFromRightAndSkipsTrustedProxies() {
        assertThat(resolver("10.0.0.1").resolve("10.0.0.1", "1.2.3.4, 10.0.0.1"))
                .isEqualTo("1.2.3.4");
    }

    @Test
    void supportsCidrTrust() {
        assertThat(resolver("192.168.0.0/24").resolve("192.168.0.5", "203.0.113.9, 192.168.0.1"))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void usesPeerWhenNoHeader() {
        assertThat(resolver().resolve("127.0.0.1", null)).isEqualTo("127.0.0.1");
    }

    @Test
    void normalizesIpv4MappedIpv6() {
        assertThat(resolver().resolve("::ffff:127.0.0.1", null)).isEqualTo("127.0.0.1");
    }
}
