package com.smartexam.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordHashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHashUtil() {
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "sha256$" + salt + "$" + sha256Hex(salt + ":" + rawPassword);
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
