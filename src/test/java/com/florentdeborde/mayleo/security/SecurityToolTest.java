package com.florentdeborde.mayleo.security;

import com.florentdeborde.mayleo.service.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test - SecurityTool")
class SecurityToolTest {

    /**
     * Use this tool to generate production-ready values.
     * * INSTRUCTIONS:
     * 1. Set your secrets (section 1) & your values to convert (section 2) below.
     * 2. Place a BREAKPOINT on the first line of section 4.
     * 3. Run this test in DEBUG MODE.
     * 4. Right-click the variables in the 'Variables' tab of your IDE to copy their values.
     */
    @Test
    @DisplayName("✅ should generate onboarding values")
    void generateOnboardingValues() {
        // --- 1. SET YOUR SECRETS HERE ---
        String salt = "YOUR_MAYLEO_KEY_SALT";
        String hmacKey = "32_CHARS_KEY_FOR_HMAC_1234567890";
        String smtpKey = "32_CHARS_KEY_FOR_SMTP_1234567890";

        // --- 2. DEFINE PLAIN TEXT VALUES TO CONVERT ---
        String rawApiKey = "client_abc_123";
        String rawHmacSecret = "my_private_hmac_secret";
        String rawSmtpPassword = "my_private_smtp_password";

        // --- 3. COMPUTATION ---
        String hashedApiKey = ApiKeyEncoder.hashSha256(rawApiKey, salt);

        EncryptionService encryptionService = new EncryptionService();
        String encryptedHmac = encryptionService.encrypt(rawHmacSecret, hmacKey);
        String encryptedSmtp = encryptionService.encrypt(rawSmtpPassword, smtpKey);

        // --- 4. BREAKPOINT HERE ---
        System.out.println("==========================================");
        System.out.println("   MAYLEO ONBOARDING VALUES GENERATOR     ");
        System.out.println("==========================================");
    }
}