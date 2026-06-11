package com.cinta.matchmateserver.exception;

import com.cinta.matchmateserver.common.BaseResponse;
import com.cinta.matchmateserver.common.ErrorCode;
import com.cinta.matchmateserver.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器
 *
 * @author CinnamoRoll
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> businessExceptionHandler(BusinessException e) {
        log.warn("Business exception: code={}, description={}", e.getCode(), e.getDescription());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return badRequest(firstValidationMessage(e.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<BaseResponse<Void>> handleBindException(BindException e) {
        return badRequest(firstValidationMessage(e.getBindingResult()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<BaseResponse<Void>> handleBadRequest(Exception e) {
        String message;
        if (e instanceof MissingServletRequestParameterException missingParameter) {
            message = "缺少必要参数: " + missingParameter.getParameterName();
        } else if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "参数类型错误: " + mismatch.getName();
        } else {
            message = "请求体格式错误，请检查 JSON 格式";
        }
        return badRequest(message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String message = "不支持的请求方法: " + Objects.toString(e.getMethod(), "");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResultUtils.error(ErrorCode.PARAM_ERROR, message, ""));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> exceptionHandler(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR));
    }

    private ResponseEntity<BaseResponse<Void>> badRequest(String description) {
        return ResponseEntity.badRequest()
                .body(ResultUtils.error(ErrorCode.PARAM_ERROR, ErrorCode.PARAM_ERROR.getMessage(), description));
    }

    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
    }
}
