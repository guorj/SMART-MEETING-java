package com.smartmeeting.exception;

import com.smartmeeting.api.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局 REST 异常处理器。
 * <p>
 * 将业务异常、参数校验失败、静态资源缺失及未捕获异常统一转换为 {@link ApiResponse} 格式返回。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，HTTP 200 但响应体携带错误码。
     *
     * @param e 业务异常
     * @return 含错误码与消息的 ApiResponse
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business error: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 {@code @Valid} 注解触发的参数校验异常。
     *
     * @param e 方法参数校验异常
     * @return HTTP 400，含字段级错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", msg);
        return ApiResponse.error(400, msg);
    }

    /**
     * 处理表单绑定校验异常。
     *
     * @param e 绑定异常
     * @return HTTP 400，含字段级错误信息
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.error(400, msg);
    }

    /**
     * 处理 Bean Validation 约束违反异常。
     *
     * @param e 约束违反异常
     * @return HTTP 400，含违反约束的描述
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * 处理静态资源未找到异常（Spring 6.x）。
     *
     * @param e 资源未找到异常
     * @return HTTP 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Static resource not found: {}", e.getResourcePath());
        return ApiResponse.error(404, "资源不存在: " + e.getResourcePath());
    }

    /**
     * 兜底处理所有未捕获异常。
     *
     * @param e 任意异常
     * @return HTTP 500，含异常消息
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.error(500, "Internal server error: " + e.getMessage());
    }
}
