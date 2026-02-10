package com.florentdeborde.mayleo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ApiKeyEncoder {

    public static String hashSha256(String plainText, String salt) {
        if (plainText == null || salt == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Combine key + salt to prevent rainbow table attacks
            String saltedKey = plainText + salt;
            byte[] encodedHash = digest.digest(saltedKey.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Fatal error: SHA-256 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
