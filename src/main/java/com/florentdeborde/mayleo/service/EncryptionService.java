package com.florentdeborde.mayleo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";

    public String encrypt(String plainText, String secretKeyStr) {
        if (plainText == null || plainText.isBlank()) return plainText;
        try {
            SecretKeySpec keySpec = buildKey(secretKeyStr);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption error", e);
        }
    }

    public String decrypt(String cipherText, String secretKeyStr) {
        if (cipherText == null || cipherText.isBlank()) return cipherText;
        try {
            SecretKeySpec keySpec = buildKey(secretKeyStr);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
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