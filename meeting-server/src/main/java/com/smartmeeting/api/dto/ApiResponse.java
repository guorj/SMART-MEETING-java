package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装体。
 * <p>
 * 所有 REST 控制器返回 {@code { code, message, data }} 结构；{@code code=0} 表示成功。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /** 业务状态码，0 为成功 */
    private int code;
    /** 人类可读说明 */
    private String message;
    /** 载荷数据，失败时通常为 null */
    private T data;

    /**
     * 构造成功响应并携带数据。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=0 的响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 数据类型
     * @return code=0、data=null 的响应
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /**
     * 构造失败响应。
     *
     * @param code    非零错误码
     * @param message 错误说明
     * @param <T>     数据类型
     * @return 含错误码的响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
