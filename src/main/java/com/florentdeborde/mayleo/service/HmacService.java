package com.florentdeborde.mayleo.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class HmacService {
    public boolean verifySignature(byte[] payload, String signature, String secret) {
        // 1. Guard clauses: if any required input is null or empty, the signature is invalid
        if (payload == null || signature == null || secret == null || signature.isBlank()) {
            return false;
        }

        // 2. Compute the expected signature
        String computed = calculateHmac(payload, secret);

        // 3. Constant-time comparison to protect from timing attacks
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String calculateHmac(byte[] data, String key) {
        try {
            javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] rawHmac = sha256_HMAC.doFinal(data);
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }
}