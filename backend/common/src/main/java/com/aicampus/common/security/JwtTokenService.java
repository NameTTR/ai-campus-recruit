package com.aicampus.common.security;

import com.aicampus.common.enums.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtTokenService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final String issuer;
    private final long ttlSeconds;

    public JwtTokenService(String secret, String issuer, long ttlSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer == null || issuer.isBlank() ? "ai-campus-recruit" : issuer;
        this.ttlSeconds = ttlSeconds <= 0 ? 86400 : ttlSeconds;
    }

    public String issue(String userId, String displayName, Role role) {
        long issuedAt = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", userId);
        payload.put("name", displayName);
        payload.put("role", role.name());
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + ttlSeconds);

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        String unsigned = headerPart + "." + payloadPart;
        return unsigned + "." + sign(unsigned);
    }

    public TokenClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtTokenException("Missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtTokenException("Malformed token");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            throw new JwtTokenException("Invalid token signature");
        }
        Map<String, Object> payload = decodeJson(parts[1]);
        if (!issuer.equals(stringClaim(payload, "iss"))) {
            throw new JwtTokenException("Invalid token issuer");
        }
        long expiresAt = longClaim(payload, "exp");
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new JwtTokenException("Token expired");
        }
        String userId = stringClaim(payload, "sub");
        String displayName = stringClaim(payload, "name");
        Role role = roleClaim(payload);
        return new TokenClaims(userId, displayName, role, expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new JwtTokenException("Failed to encode token", ex);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return OBJECT_MAPPER.readValue(URL_DECODER.decode(value), new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new JwtTokenException("Failed to decode token", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new JwtTokenException("Failed to sign token", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigestUtil.constantTimeEquals(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String stringClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new JwtTokenException("Missing token claim: " + name);
        }
        return value.toString();
    }

    private long longClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(stringClaim(payload, name));
        } catch (NumberFormatException ex) {
            throw new JwtTokenException("Invalid token claim: " + name, ex);
        }
    }

    private Role roleClaim(Map<String, Object> payload) {
        String value = stringClaim(payload, "role");
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new JwtTokenException("Invalid token claim: role", ex);
        }
    }

    public record TokenClaims(String userId, String displayName, Role role, long expiresAt) {
    }
}
