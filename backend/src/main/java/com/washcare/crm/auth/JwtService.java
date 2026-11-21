package com.washcare.crm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtService.class);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final long TOKEN_LIFETIME_SECONDS = 60 * 60 * 8;

    private final byte[] secret;
    private final ObjectMapper objectMapper;

    public JwtService(
            @Value("${washcare.jwt.secret}") String secret,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        if (secret == null || secret.isBlank()) {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
            LOGGER.warn("WASHCARE_JWT_SECRET is not configured. Using an ephemeral local key; active tokens will expire when the API restarts.");
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("WASHCARE_JWT_SECRET must be at least 32 bytes.");
        }
    }

    public String createToken(UserAccount user) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.getId().toString(),
                    "email", user.getEmail(),
                    "name", user.getFullName(),
                    "role", user.getRole().name(),
                    "iat", Instant.now().getEpochSecond(),
                    "exp", Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond())));
            String unsignedToken = header + "." + payload;
            return unsignedToken + "." + ENCODER.encodeToString(sign(unsignedToken));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create an authentication token.", exception);
        }
    }

    public TokenClaims parseToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid authentication token.");
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] suppliedSignature = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
                throw new IllegalArgumentException("Invalid authentication token signature.");
            }

            Map<?, ?> header = objectMapper.readValue(DECODER.decode(parts[0]), Map.class);
            if (!"HS256".equals(header.get("alg"))) {
                throw new IllegalArgumentException("Unsupported authentication token algorithm.");
            }

            Map<?, ?> claims = objectMapper.readValue(DECODER.decode(parts[1]), Map.class);
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Authentication token has expired.");
            }

            return new TokenClaims(
                    Long.parseLong(claims.get("sub").toString()),
                    claims.get("email").toString(),
                    claims.get("role").toString());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid or expired authentication token.", exception);
        }
    }

    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    public record TokenClaims(Long userId, String email, String role) {
    }
}
