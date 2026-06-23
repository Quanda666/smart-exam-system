package com.smartexam.exception;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler({DatabaseUnavailableException.class, CannotGetJdbcConnectionException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(ErrorCode.SERVICE_UNAVAILABLE, "数据库连接不可用，请检查本地或云端数据源配置"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_STATE, ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("未处理的异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.SERVER_ERROR, "服务器处理请求失败：" + ex.getClass().getSimpleName() + "（请求ID：" + getCurrentRequestId() + "）"));
    }

    private String getCurrentRequestId() {
        try {
            return org.slf4j.MDC.get("traceId");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String formatFieldError(FieldError error) {
        return error.getDefaultMessage() == null ? error.getField() + "参数不合法" : error.getDefaultMessage();
    }
}
