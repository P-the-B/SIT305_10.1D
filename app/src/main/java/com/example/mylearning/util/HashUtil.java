package com.example.mylearning.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    // Salted hash — always use this for passwords.
    // Salt is the user's lowercase email so two users with the same password
    // produce different hashes, and rainbow tables are useless without the email.
    public static String sha256(String salt, String input) {
        return hash(salt + input);
    }

    // Unsalted hash — kept for security answers, which don't benefit from email salt.
    public static String sha256(String input) {
        return hash(input);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}