package com.example.startup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthTokenServiceTests {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    @Test
    void issuedTokenAuthenticatesItsOwner() {
        AuthTokenService service = new AuthTokenService(SECRET, 60);

        String token = service.issueToken("reporter_1");

        assertEquals("reporter_1", service.requireLoginId("Bearer " + token));
    }

    @Test
    void tamperedTokenIsRejected() {
        AuthTokenService service = new AuthTokenService(SECRET, 60);
        String token = service.issueToken("reporter_1");
        int signatureStart = token.lastIndexOf('.') + 1;
        int tamperIndex = signatureStart + 3;
        char replacement = token.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, tamperIndex)
                + replacement + token.substring(tamperIndex + 1);

        assertThrows(ResponseStatusException.class,
                () -> service.requireLoginId("Bearer " + tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        AuthTokenService service = new AuthTokenService(SECRET, -1);
        String token = service.issueToken("reporter_1");

        assertThrows(ResponseStatusException.class,
                () -> service.requireLoginId("Bearer " + token));
    }
}
