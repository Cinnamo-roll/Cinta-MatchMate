package com.cinoo.matchmateserver.exception;

import com.cinoo.matchmateserver.common.BaseResponse;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器
 *
 * @author CintaOvO
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> businessExceptionHandler(BusinessException e) {
        log.warn("Business exception: code={}, description={}", e.getCode(), e.getDescription());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription()));
    }

    /**
     * 参数校验异常处理
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return badRequest(firstValidationMessage(e.getBindingResult()));
    }

    /**
     * 参数绑定异常处理
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<BaseResponse<Void>> handleBindException(BindException e) {
        return badRequest(firstValidationMessage(e.getBindingResult()));
    }

    /**
     * 参数格式错误处理
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<BaseResponse<Void>> handleBadRequest(Exception e) {
        String message;
        if (e instanceof MissingServletRequestParameterException missingParameter) {
            message = "缺少必要参数: " + missingParameter.getParameterName();
        } else if (e instanceof MissingServletRequestPartException missingPart) {
            message = "缺少必要文件: " + missingPart.getRequestPartName();
        } else if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "参数类型错误: " + mismatch.getName();
        } else {
            message = "请求体格式错误，请检查 JSON 格式";
        }
        return badRequest(message);
    }

    /**
     * 资源未找到处理
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.NOT_FOUND));
    }

    /**
     * 请求方法不支持处理
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String message = "不支持的请求方法: " + Objects.toString(e.getMethod(), "");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResultUtils.error(ErrorCode.PARAM_ERROR, message, ""));
    }

    /**
     * 上传文件大小超出限制处理
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Void>> handleMaxUploadSizeExceeded() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ResultUtils.error(ErrorCode.FILE_SIZE_EXCEEDED));
    }

    /**
     * 未处理的异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> exceptionHandler(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR));
    }

    /**
     * 参数错误处理
     */
    private ResponseEntity<BaseResponse<Void>> badRequest(String description) {
        return ResponseEntity.badRequest()
                .body(ResultUtils.error(ErrorCode.PARAM_ERROR, ErrorCode.PARAM_ERROR.getMessage(), description));
    }

    /**
     * 获取第一个参数错误信息
     */
    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
    }
}
