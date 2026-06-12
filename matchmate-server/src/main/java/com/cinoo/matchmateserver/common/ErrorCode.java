package com.cinoo.matchmateserver.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Common business error codes.
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "ok", ""),
    PARAM_ERROR(40000, HttpStatus.BAD_REQUEST, "Parameter error", ""),
    NOT_LOGIN(40100, HttpStatus.UNAUTHORIZED, "Not logged in", ""),
    NO_AUTH(40300, HttpStatus.FORBIDDEN, "No permission", ""),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "Resource not found", ""),
    SYSTEM_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "Internal system error", "");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
    private final String description;

    ErrorCode(int code, HttpStatus httpStatus, String message, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
        this.description = description;
    }
}
