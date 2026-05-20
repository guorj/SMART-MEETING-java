package com.smartmeeting.exception;

import lombok.Getter;

/**
 * 业务异常，携带自定义错误码与消息。
 * <p>
 * 由 {@link GlobalExceptionHandler} 捕获后转换为 {@code ApiResponse.error(code, message)} 返回。
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 业务错误码（如 401、403 或自定义正整数） */
    private final int code;

    /**
     * 构造指定错误码的业务异常。
     *
     * @param code    错误码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造默认错误码（1）的业务异常。
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        this(1, message);
    }
}
