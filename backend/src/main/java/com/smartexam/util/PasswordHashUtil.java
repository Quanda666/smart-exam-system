package com.smartexam.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordHashUtil {

    private PasswordHashUtil() {
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        if (storedHash.startsWith("{noop}")) {
            return rawPassword.equals(storedHash.substring("{noop}".length()));
        }

        if (storedHash.startsWith("sha256$")) {
            String[] parts = storedHash.split("\\$", 3);
            if (parts.length != 3) {
                return false;
            }
            String salt = parts[1];
            String expected = parts[2];
            return sha256Hex(salt + ":" + rawPassword).equalsIgnoreCase(expected);
        }

        return rawPassword.equals(storedHash);
    }

    public static String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", ex);
        }
    }
}
