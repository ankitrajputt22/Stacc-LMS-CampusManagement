package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorsPropertiesTest {

    @Test
    void exactOriginsAreAccepted() {
        CorsProperties properties = new CorsProperties(
                List.of("http://localhost:5173", " https://stacc.example ", "http://127.0.0.1:3000"));

        assertEquals(
                List.of("http://localhost:5173", "https://stacc.example", "http://127.0.0.1:3000"),
                properties.allowedOrigins());
    }

    @Test
    void noOriginsMeansNoOtherAddressIsAllowed() {
        assertTrue(new CorsProperties(null).allowedOrigins().isEmpty());
        assertTrue(new CorsProperties(List.of()).allowedOrigins().isEmpty());
        assertTrue(new CorsProperties(Arrays.asList("", "  ")).allowedOrigins().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*",
        "http://*",
        "http://*.example.com",
        "https://localhost:*",
        "http://localhost:5173/",
        "http://localhost:5173/app",
        "http://localhost:5173?x=1",
        "http://user@localhost:5173",
        "localhost:5173",
        "ftp://localhost",
        "null",
        "not an origin"
    })
    void wildcardsAndAnythingThatIsNotAnExactOriginAreRefused(String origin) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new CorsProperties(List.of(origin)));

        assertTrue(exception.getMessage().startsWith("FRONTEND_ORIGIN must list exact origins"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(List.of("http://localhost:5173", origin)));
    }
}
