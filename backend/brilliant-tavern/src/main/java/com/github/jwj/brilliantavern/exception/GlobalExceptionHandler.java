package com.github.jwj.brilliantavern.exception;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getCode())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "用户名或密码错误"));
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("参数校验失败: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "参数校验失败"));
    }

    /**
     * 处理JSON序列化异常
     */
    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageConversionException(HttpMessageConversionException e) {
        log.error("JSON序列化异常: ", e);
        
        // 检查是否是Hibernate代理对象序列化问题
        if (e.getCause() instanceof InvalidDefinitionException) {
            InvalidDefinitionException ide = (InvalidDefinitionException) e.getCause();
            if (ide.getMessage().contains("ByteBuddyInterceptor")) {
                log.error("Hibernate代理对象序列化失败，请检查实体类的JSON序列化配置");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error(500, "数据序列化失败"));
            }
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "数据格式转换失败"));
    }

    /**
     * 处理Jackson序列化异常
     */
    @ExceptionHandler(InvalidDefinitionException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidDefinitionException(InvalidDefinitionException e) {
        log.error("Jackson序列化异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "数据序列化失败"));
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception e) {
        log.error("未知异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
