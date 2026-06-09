package com.aicampus.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicampus.common.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SECRET = "common-test-secret-that-is-long-enough";
    private static final String ISSUER = "ai-campus-test";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, ISSUER, 86400);

    @Test
    void issuesAndVerifiesSignedToken() {
        String token = jwtTokenService.issue("S001", "Student", Role.STUDENT);

        JwtTokenService.TokenClaims claims = jwtTokenService.verify(token);

        assertThat(claims.userId()).isEqualTo("S001");
        assertThat(claims.displayName()).isEqualTo("Student");
        assertThat(claims.role()).isEqualTo(Role.STUDENT);
        assertThat(claims.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void rejectsInvalidIssuer() {
        String token = signedToken(ISSUER + "-other", "S001", "Student", "STUDENT", 3600);

        assertThatThrownBy(() -> jwtTokenService.verify(token))
                .isInstanceOf(JwtTokenException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void rejectsExpiredToken() {
        String token = signedToken(ISSUER, "S001", "Student", "STUDENT", -1);

        assertThatThrownBy(() -> jwtTokenService.verify(token))
                .isInstanceOf(JwtTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsInvalidRoleClaim() {
        String token = signedToken(ISSUER, "S001", "Student", "ROOT", 3600);

        assertThatThrownBy(() -> jwtTokenService.verify(token))
                .isInstanceOf(JwtTokenException.class)
                .hasMessageContaining("role");
    }

    private String signedToken(String issuer, String subject, String name, String role, long expiresInSeconds) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", subject);
        payload.put("name", name);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + expiresInSeconds);

        String unsigned = encode(header) + "." + encode(payload);
        return unsigned + "." + sign(unsigned);
    }

    private String encode(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
