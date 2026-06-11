package com.cinta.matchmateserver.common;

import lombok.Getter;

/**
 * Common business error codes.
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "ok", ""),
    PARAM_ERROR(40000, "Parameter error", ""),
    NOT_LOGIN(40100, "Not logged in", ""),
    NO_AUTH(40300, "No permission", ""),
    NOT_FOUND(40400, "Resource not found", ""),
    SYSTEM_ERROR(50000, "Internal system error", "");

    private final int code;
    private final String message;
    private final String description;

    ErrorCode(int code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }
}
