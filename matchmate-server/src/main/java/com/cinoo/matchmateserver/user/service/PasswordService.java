package com.cinoo.matchmateserver.user.service;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * BCrypt 密码编码服务。
 */
@Component
public class PasswordService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

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
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private void validateLength(String rawPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码编码后不能超过 72 字节");
        }
    }
}
