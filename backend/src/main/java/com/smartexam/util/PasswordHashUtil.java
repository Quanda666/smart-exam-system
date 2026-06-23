package com.smartexam.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_BITS = 256;

    private PasswordHashUtil() {
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + salt + "$" + pbkdf2Hex(rawPassword, salt, PBKDF2_ITERATIONS);
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        if (storedHash.startsWith("{noop}")) {
            return constantTimeEquals(rawPassword, storedHash.substring("{noop}".length()));
        }

        if (storedHash.startsWith("pbkdf2$")) {
            String[] parts = storedHash.split("\\$", 4);
            if (parts.length != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[1]);
                String actual = pbkdf2Hex(rawPassword, parts[2], iterations);
                return constantTimeEquals(actual, parts[3]);
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        if (storedHash.startsWith("sha256$")) {
            String[] parts = storedHash.split("\\$", 3);
            if (parts.length != 3) {
                return false;
            }
            String salt = parts[1];
            String expected = parts[2];
            return constantTimeEquals(sha256Hex(salt + ":" + rawPassword), expected);
        }

        return constantTimeEquals(rawPassword, storedHash);
    }

    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return true;
        }
        if (!storedHash.startsWith("pbkdf2$")) {
            return true;
        }
        String[] parts = storedHash.split("\\$", 4);
        if (parts.length != 4) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < PBKDF2_ITERATIONS;
        } catch (NumberFormatException ex) {
            return true;
        }
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

    private static String pbkdf2Hex(String rawPassword, String saltHex, int iterations) {
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("当前 Java 环境不支持 PBKDF2 密码哈希", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
