package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.model.AuthRoleCodes;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RequirePermission} 权限拦截器
 * <p>校验顺序：</p>
 * <ol>
 *   <li>SecurityContext 中 authorities（角色 code）包含 super_admin → 直接放行（内置超管）</li>
 *   <li>authorities 直接包含权限 code → 放行（预留 token 携带权限点的场景）</li>
 *   <li>兜底走 DB：用户 → 角色 → 权限点（sys_user_role / sys_role_permission / sys_permission），
 *       结果经 {@link PermissionCache} 缓存（Cache-Aside）</li>
 *   <li>均不满足 → 抛 BizException(ErrorCode.FORBIDDEN)，由 GlobalExceptionHandler 返回 403 Result JSON</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    /** 内置超级管理员角色 code，拥有全部权限 */
    public static final String SUPER_ADMIN = AuthRoleCodes.SUPER_ADMIN;

    private final PermissionCache permissionCache;
    private final RoleRepository roleRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (annotation.requireLogin()
                && (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String required = annotation.value();
        if (!StringUtils.hasText(required)) {
            return true;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (authorities.contains(SUPER_ADMIN) || authorities.contains(required)) {
            return true;
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser
                && permissionCache.getPermissionCodes(loginUser.getUserId(),
                () -> roleRepository.listPermissionCodesByUserId(loginUser.getUserId())).contains(required)) {
            return true;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }
}
