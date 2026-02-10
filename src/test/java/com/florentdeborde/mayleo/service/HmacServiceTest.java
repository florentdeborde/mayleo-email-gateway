package com.florentdeborde.mayleo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test - HmacService")
class HmacServiceTest {

    private HmacService hmacService;
    private static final String SHARED_SECRET = "test-secret-key-1234567890";
    private static final String WRONG_SECRET = "wrong-secret-key-9876543210";
    private static final String JSON_PAYLOAD = "{\"to\":\"user@example.com\",\"subject\":\"Test\"}";

    @BeforeEach
    void setUp() {
        hmacService = new HmacService();
    }

    @Test
    @DisplayName("✅ verifySignature: Should return true for valid signature")
    void verifySignature_ShouldReturnTrueForValidSignature() {
        // GIVEN
        byte[] payloadBytes = JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String validSignature = hmacService.calculateHmac(payloadBytes, SHARED_SECRET);

        // WHEN
        boolean isValid = hmacService.verifySignature(payloadBytes, validSignature, SHARED_SECRET);

        // THEN
        assertTrue(isValid, "Signature should be validated successfully");
    }

    @Test
    @DisplayName("❌ verifySignature: Should return false for invalid secret")
    void verifySignature_ShouldReturnFalseForInvalidSecret() {
        // GIVEN
        byte[] payloadBytes = JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String validSignature = hmacService.calculateHmac(payloadBytes, SHARED_SECRET);

        // WHEN
        boolean isValid = hmacService.verifySignature(payloadBytes, validSignature, WRONG_SECRET);

        // THEN
        assertFalse(isValid, "Signature check should fail with an incorrect secret");
    }

    @Test
    @DisplayName("❌ verifySignature: Should return false for tampered payload")
    void verifySignature_ShouldReturnFalseForTamperedPayload() {
        // GIVEN
        byte[] originalBytes = JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String validSignature = hmacService.calculateHmac(originalBytes, SHARED_SECRET);

        String tamperedPayload = JSON_PAYLOAD.replace("Test", "Hacked");
        byte[] tamperedBytes = tamperedPayload.getBytes(StandardCharsets.UTF_8);

        // WHEN
        boolean isValid = hmacService.verifySignature(tamperedBytes, validSignature, SHARED_SECRET);

        // THEN
        assertFalse(isValid, "Signature should fail if the payload was modified after signing");
    }

    @Test
    @DisplayName("❌ verifySignature: Should return false for null or empty inputs")
    void verifySignature_ShouldHandleEdgeCases() {
        byte[] payload = JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8);

        assertFalse(hmacService.verifySignature(payload, null, SHARED_SECRET));
        assertFalse(hmacService.verifySignature(payload, "any", null));
    }
}