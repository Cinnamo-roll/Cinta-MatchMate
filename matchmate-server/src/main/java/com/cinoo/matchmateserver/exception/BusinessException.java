package com.cinoo.matchmateserver.exception;

import com.cinoo.matchmateserver.common.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 自定义异常类
 *
 * @author CinnamoRoll
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;
    private final String description;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.description = errorCode.getDescription();
    }

    public BusinessException(ErrorCode errorCode, String description) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.description = description;
    }
}
