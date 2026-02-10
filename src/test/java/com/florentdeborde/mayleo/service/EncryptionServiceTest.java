package com.florentdeborde.mayleo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test - EncryptionService")
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String KEY_A = "12345678901234567890123456789012";
    private static final String KEY_B = "98765432109876543210987654321098";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    @Test
    @DisplayName("✅ encrypt & decrypt: Should restore original text using same key")
    void encryptDecrypt_ShouldRestoreOriginalText() {
        // GIVEN
        String originalText = "my-secret-password-2026!";

        // WHEN
        String encrypted = encryptionService.encrypt(originalText, KEY_A);
        String decrypted = encryptionService.decrypt(encrypted, KEY_A);

        // THEN
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    @DisplayName("✅ isolation: Same text encrypted with different keys should differ")
    void isolation_DifferentKeysShouldProduceDifferentCipherTexts() {
        // GIVEN
        String secret = "constant-secret";

        // WHEN
        String cipherA = encryptionService.encrypt(secret, KEY_A);
        String cipherB = encryptionService.encrypt(secret, KEY_B);

        // THEN
        assertNotEquals(cipherA, cipherB, "Cipher texts should be different for different keys");
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(cipherA, KEY_B),
                "Should fail to decrypt Cipher A with Key B");
    }

    @Test
    @DisplayName("✅ handle null or blank: Should return input as-is")
    void handleNullOrBlank_ShouldReturnInput() {
        // Test null
        assertNull(encryptionService.encrypt(null, KEY_A));

        // Test blank string
        String blankInput = "   ";
        assertEquals(blankInput, encryptionService.encrypt(blankInput, KEY_A));
    }

    @Test
    @DisplayName("❌ invalid key: Should throw exception if key length is incorrect")
    void shouldThrowExceptionOnInvalidKeyLength() {
        String invalidKey = "too-short";
        String secret = "test";

        assertThrows(RuntimeException.class, () -> encryptionService.encrypt(secret, invalidKey));
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt("any", invalidKey));
    }

    @Test
    @DisplayName("❌ decrypt: Should throw exception if cipher text is malformed")
    void decrypt_ShouldThrowOnMalformedText() {
        String malformedBase64 = "ThisIsNotBase64!";
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(malformedBase64, KEY_A));
    }
}