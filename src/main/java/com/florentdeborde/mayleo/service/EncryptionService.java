package com.florentdeborde.mayleo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText, String secretKeyStr) {
        if (plainText == null || plainText.isBlank())
            return plainText;
        try {
            SecretKeySpec keySpec = buildKey(secretKeyStr);

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String encodedIv = Base64.getEncoder().encodeToString(iv);
            String encodedCipher = Base64.getEncoder().encodeToString(cipherText);

            return encodedIv + ":" + encodedCipher;
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption error", e);
        }
    }

    public String decrypt(String cipherText, String secretKeyStr) {
        if (cipherText == null || cipherText.isBlank())
            return cipherText;
        try {
            SecretKeySpec keySpec = buildKey(secretKeyStr);

            // Check if string has the IV separator for GCM
            if (cipherText.contains(":")) {
                String[] parts = cipherText.split(":", 2);
                byte[] iv = Base64.getDecoder().decode(parts[0]);
                byte[] cipherBytes = Base64.getDecoder().decode(parts[1]);

                GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

                return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
            } else {
                // Fallback to old AES/ECB for backward compatibility
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
                return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption error", e);
        }
    }

    private SecretKeySpec buildKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES Key must be 16, 24, or 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}