package com.cinta.matchmateserver.service;

import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 密码编码服务，支持将历史 SHA-256 密码平滑迁移到 BCrypt。
 */
@Component
public class PasswordService {

    private static final String LEGACY_SALT = "cinta";
    private static final int LEGACY_HASH_LENGTH = 64;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        validateLength(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        validateLength(rawPassword);
        if (encodedPassword == null) {
            return false;
        }
        if (isLegacyHash(encodedPassword)) {
            return MessageDigest.isEqual(
                    legacyEncode(rawPassword).getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8)
            );
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public boolean needsUpgrade(String encodedPassword) {
        return encodedPassword != null
                && (isLegacyHash(encodedPassword) || passwordEncoder.upgradeEncoding(encodedPassword));
    }

    private boolean isLegacyHash(String encodedPassword) {
        return encodedPassword.length() == LEGACY_HASH_LENGTH
                && HEX_PATTERN.matcher(encodedPassword).matches();
    }

    private String legacyEncode(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((LEGACY_SALT + rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void validateLength(String rawPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码编码后不能超过 72 字节");
        }
    }
}
