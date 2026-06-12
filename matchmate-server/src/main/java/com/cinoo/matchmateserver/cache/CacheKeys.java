package com.cinoo.matchmateserver.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class CacheKeys {

    public static final String ALL = "all";

    private CacheKeys() {
    }

    public static String user(long userId) {
        return Long.toString(userId);
    }

    public static String recommendation(int limit) {
        return Integer.toString(limit);
    }

    public static String search(List<String> normalizedTags) {
        List<String> sortedTags = normalizedTags.stream().sorted().toList();
        return sha256(String.join("\u001f", sortedTags));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
