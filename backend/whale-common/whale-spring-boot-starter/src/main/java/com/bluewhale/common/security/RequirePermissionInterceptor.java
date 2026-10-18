package com.bluewhale.common.security;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 通用 {@link RequirePermission} 权限码头拦截器（AUTH-06）。
 *
 * <p>判定顺序：无 {@link RequirePermission} 注解或注解 {@code value} 为空 → 直接放行；
 * 读取权限码头 {@code whale.permission.header.codes-header}（缺省回落 {@code X-User-Roles}），
 * 集合含 {@code super_admin} 或含注解 {@code value} → 放行；否则抛
 * {@link BizException}({@link ErrorCode#FORBIDDEN})，由 {@code GlobalExceptionHandler} 返回 403。</p>
 *
 * <p><b>fail-closed</b>：权限码头缺失/为空时集合为空 → 拒绝（可用性让位于安全）。</p>
 *
 * @author BlueWhale
 */
public class RequirePermissionInterceptor implements HandlerInterceptor {

    /** 内置超级管理员角色 code，拥有全部权限 */
    public static final String SUPER_ADMIN = "super_admin";

    private final PermissionHeaderProperties properties;

    /**
     * 构造权限码头拦截器
     *
     * @param properties 权限码头判定配置
     */
    public RequirePermissionInterceptor(PermissionHeaderProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null || !StringUtils.hasText(annotation.value())) {
            return true;
        }
        Set<String> codes = resolveCodes(request);
        if (codes.contains(SUPER_ADMIN) || codes.contains(annotation.value())) {
            return true;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    /**
     * 解析权限码头集合：优先配置的头名，缺省回落用户角色头；均缺失返回空集合
     *
     * @param request 当前请求
     * @return 权限码/角色码集合
     */
    private Set<String> resolveCodes(HttpServletRequest request) {
        String codes = request.getHeader(properties.getCodesHeader());
        if (!StringUtils.hasText(codes)) {
            codes = request.getHeader(WebHeaders.USER_ROLES);
        }
        return WebHeaders.splitCodes(codes);
    }
}
