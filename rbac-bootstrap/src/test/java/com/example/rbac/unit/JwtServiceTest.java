package com.example.rbac.unit;

import com.example.rbac.api.auth.VerifiedToken;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.auth.config.JwtProperties;
import com.example.rbac.auth.internal.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String KEY_1 = "test-key-one-at-least-32-bytes-0123456789";
    private static final String KEY_2 = "test-key-two-at-least-32-bytes-0123456789";

    private JwtService service(Map<String, String> keys, String activeKid, Duration ttl) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("rbac-system");
        properties.setAudience("rbac-admin");
        properties.setKeys(keys);
        properties.setActiveKid(activeKid);
        properties.setTtl(ttl);
        JwtService jwtService = new JwtService(properties);
        jwtService.initialize();
        return jwtService;
    }

    private Map<String, String> keys(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void signsAndVerifiesAllRequiredClaims() {
        JwtService jwt = service(keys("1", KEY_1), "1", Duration.ofHours(2));
        String token = jwt.sign(7L, 3L, "alice");
        VerifiedToken verified = jwt.verify(token);
        assertThat(verified.userId()).isEqualTo(7L);
        assertThat(verified.tokenVersion()).isEqualTo(3L);
        assertThat(verified.username()).isEqualTo("alice");
        assertThat(verified.keyId()).isEqualTo("1");
    }

    @Test
    void tamperedTokenIsRejectedAs40100() {
        JwtService jwt = service(keys("1", KEY_1), "1", Duration.ofHours(2));
        String token = jwt.sign(1L, 0L, "admin");
        assertThatThrownBy(() -> jwt.verify(token + "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(40100);
    }

    @Test
    void removingOldKeyMakesOldTokenInvalidAs40100() {
        JwtService old = service(keys("1", KEY_1), "1", Duration.ofHours(2));
        String oldToken = old.sign(1L, 0L, "admin");
        JwtService rotated = service(keys("2", KEY_2), "2", Duration.ofHours(2));
        assertThatThrownBy(() -> rotated.verify(oldToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(40100);
    }

    @Test
    void dualKeyPeriodAcceptsOldToken() {
        JwtService old = service(keys("1", KEY_1), "1", Duration.ofHours(2));
        String oldToken = old.sign(1L, 0L, "admin");
        JwtService dual = service(keys("1", KEY_1, "2", KEY_2), "2", Duration.ofHours(2));
        assertThat(dual.verify(oldToken).keyId()).isEqualTo("1");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(keys("1", KEY_1), "1", Duration.ofSeconds(-120));
        String token = jwt.sign(1L, 0L, "admin");
        assertThatThrownBy(() -> jwt.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(40100);
    }
}
