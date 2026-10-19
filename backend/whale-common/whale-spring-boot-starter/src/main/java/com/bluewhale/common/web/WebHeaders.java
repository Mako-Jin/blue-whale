package com.bluewhale.common.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 全服务共享的链路与权限请求头常量及取值工具（REQ-SEC-006 / AUTH-06）。
 *
 * <p>{@code X-Trace-Id} 由网关 {@code TraceIdGlobalFilter} 注入，{@code X-User-Id} / {@code X-Username}
 * 由 {@code JwtAuthGlobalFilter} 注入，{@code X-Permission-Codes} 由 {@code PermissionHeaderGlobalFilter}
 * 注入；Filter / Interceptor / 权限拦截器统一引用本类，避免多份重复定义与取值口径漂移。</p>
 *
 * @author BlueWhale
 */
public final class WebHeaders {

    /** 链路追踪ID请求头 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** 用户ID请求头 */
    public static final String USER_ID = "X-User-Id";
    /** 用户名请求头 */
    public static final String USERNAME = "X-Username";
    /** 用户角色请求头（角色 code 逗号拼接） */
    public static final String USER_ROLES = "X-User-Roles";
    /** 权限码请求头（权限码逗号拼接） */
    public static final String PERMISSION_CODES = "X-Permission-Codes";
    /** 租户ID请求头（多租户行级隔离；网关不注入，客户端可选传递，缺省回落默认租户） */
    public static final String TENANT_ID = "X-Tenant-Id";

    /** 工具类不允许实例化 */
    private WebHeaders() {
    }

    /**
     * 读取请求头并对首尾空白去噪
     *
     * @param request 当前请求，可为 {@code null}
     * @param name 请求头名称
     * @return 去除首尾空白后的头值；请求为空或头值无文本时返回 {@code null}
     */
    public static String header(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 读取链路追踪ID头
     *
     * @param request 当前请求
     * @return 链路追踪ID；缺失时返回 {@code null}
     */
    public static String traceId(HttpServletRequest request) {
        return header(request, TRACE_ID);
    }

    /**
     * 读取用户名头
     *
     * @param request 当前请求
     * @return 用户名；缺失时返回 {@code null}
     */
    public static String username(HttpServletRequest request) {
        return header(request, USERNAME);
    }

    /**
     * 读取用户ID头（原样文本，是否可解析为数字由调用方判断）
     *
     * @param request 当前请求
     * @return 用户ID文本；缺失时返回 {@code null}
     */
    public static String userId(HttpServletRequest request) {
        return header(request, USER_ID);
    }

    /**
     * 读取租户ID头（原样文本，是否可解析为数字由调用方判断）
     *
     * @param request 当前请求
     * @return 租户ID文本；缺失时返回 {@code null}
     */
    public static String tenantId(HttpServletRequest request) {
        return header(request, TENANT_ID);
    }

    /**
     * 拆分逗号分隔的编码集合（去除空白项）
     *
     * @param csv 逗号分隔的编码串，可为 {@code null} 或空
     * @return 去重后的编码集合；入参无有效内容时返回空集合
     */
    public static Set<String> splitCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }
}
