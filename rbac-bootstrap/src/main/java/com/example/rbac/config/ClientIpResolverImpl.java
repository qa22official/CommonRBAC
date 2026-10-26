package com.example.rbac.config;

import com.example.rbac.api.support.ClientIpResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * The only client-IP parser (02 §15). X-Forwarded-For is consulted only when
 * the TCP peer is in the configured trusted-proxy list; values are walked from
 * right to left so a client cannot prepend a forged address.
 */
@Component
public class ClientIpResolverImpl implements ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolverImpl.class);

    private final List<Cidr> trusted;
    private final Counter untrustedForwarded;

    public ClientIpResolverImpl(ClientIpProperties properties, MeterRegistry meterRegistry) {
        List<Cidr> parsed = new ArrayList<>();
        for (String entry : properties.getTrustedProxies()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                parsed.add(Cidr.parse(entry.trim()));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid trusted proxy entry '{}': {}", entry, ex.getMessage());
            }
        }
        this.trusted = List.copyOf(parsed);
        this.untrustedForwarded = Counter.builder("rbac.http.untrusted_forwarded_for")
                .description("Requests whose X-Forwarded-For was ignored because the peer is not trusted")
                .register(meterRegistry);
    }

    @Override
    public String resolve(String remoteAddress, String forwardedFor) {
        String peer = normalize(remoteAddress);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer;
        }
        if (!isTrusted(peer)) {
            untrustedForwarded.increment();
            log.debug("Ignoring X-Forwarded-For from untrusted peer {}: {}", peer, forwardedFor);
            return peer;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalize(hops[i]);
            if (hop.isEmpty()) {
                continue;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return normalize(hops[0]);
    }

    private boolean isTrusted(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        for (Cidr cidr : trusted) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String address) {
        if (address == null) {
            return "";
        }
        String value = address.trim();
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        return value;
    }

    private record Cidr(byte[] network, int prefixBits) {

        static Cidr parse(String value) {
            String address = value;
            int prefix = -1;
            int slash = value.indexOf('/');
            if (slash > 0) {
                address = value.substring(0, slash);
                prefix = Integer.parseInt(value.substring(slash + 1));
            }
            InetAddress inet = literal(address);
            byte[] bytes = inet.getAddress();
            int bits = bytes.length * 8;
            if (prefix < 0) {
                prefix = bits;
            }
            if (prefix < 0 || prefix > bits) {
                throw new IllegalArgumentException("prefix out of range");
            }
            return new Cidr(bytes, prefix);
        }

        boolean contains(String candidate) {
            InetAddress inet;
            try {
                inet = literal(candidate);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            byte[] bytes = inet.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int remaining = prefixBits;
            for (int i = 0; i < bytes.length && remaining > 0; i++) {
                int bits = Math.min(8, remaining);
                int mask = 0xFF << (8 - bits);
                if ((bytes[i] & mask) != (network[i] & mask)) {
                    return false;
                }
                remaining -= bits;
            }
            return true;
        }

        private static InetAddress literal(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("blank address");
            }
            if (!value.matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("not an IP literal");
            }
            try {
                return InetAddress.getByName(value);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("invalid address", ex);
            }
        }
    }
}
