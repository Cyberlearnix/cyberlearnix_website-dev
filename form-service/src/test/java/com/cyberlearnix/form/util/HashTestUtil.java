package com.cyberlearnix.form.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HashTestUtil {
    private HashTestUtil() {}

    public static String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.reset();
            md.update(input.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Hash computation failed", e);
        }
    }
}
