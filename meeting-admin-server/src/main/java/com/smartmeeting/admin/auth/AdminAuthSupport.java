package com.smartmeeting.admin.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 Admin 请求上下文读取 OAuth 操作者信息。
 */
public final class AdminAuthSupport {

    public static final String ATTR_OPERATOR_ID = "adminOperatorId";
    public static final String ATTR_OPERATOR_NAME = "adminOperatorName";

    private AdminAuthSupport() {
    }

    public static String operatorId(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_OPERATOR_ID);
        return v != null ? String.valueOf(v) : "admin-token";
    }

    public static String operatorName(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_OPERATOR_NAME);
        return v != null ? String.valueOf(v) : "admin-token";
    }
}
