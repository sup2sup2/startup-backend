package com.example.startup.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] secret;
    private final long expirationSeconds;

    public AuthTokenService(
            @Value("${auth.token.secret}") String secret,
            @Value("${auth.token.expiration-seconds}") long expirationSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be at least 32 characters long");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String issueToken(String loginId) {
        String encodedLoginId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(loginId.getBytes(StandardCharsets.UTF_8));
        long expiresAt = Instant.now().getEpochSecond() + expirationSeconds;
        String payload = encodedLoginId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    public String requireLoginId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }

        String[] parts = authorizationHeader.substring(BEARER_PREFIX.length()).split("\\.");
        if (parts.length != 3) {
            throw unauthorized();
        }

        try {
            String payload = parts[0] + "." + parts[1];
            byte[] expectedSignature = Base64.getUrlDecoder().decode(sign(payload));
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            long expiresAt = Long.parseLong(parts[1]);

            if (!MessageDigest.isEqual(expectedSignature, actualSignature)
                    || expiresAt < Instant.now().getEpochSecond()) {
                throw unauthorized();
            }

            return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create authentication token", exception);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요하거나 인증이 만료되었습니다.");
    }
}
