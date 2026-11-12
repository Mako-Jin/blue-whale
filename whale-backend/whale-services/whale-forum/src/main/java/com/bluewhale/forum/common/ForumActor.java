package com.bluewhale.forum.common;

import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前操作者（由 controller 从请求头解析后传入 service）。
 *
 * <p>service 不直接依赖 {@code HttpServletRequest}：权限与部门信息在入口处一次性解析，
 * 业务判定只吃这一个对象，单测可以直接构造，不必起 Servlet 容器。</p>
 *
 * @param userId 用户ID（必须）
 * @param permissions 权限码与角色码的并集（含 super_admin 时视为全权）
 * @param deptId 部门ID（网关暂未注入时为 null，DEPT 可见版块按不可见处理）
 * @author BlueWhale
 */
public record ForumActor(Long userId, Set<String> permissions, String deptId) {

    /** 超管角色码（与权限拦截器同口径） */
    public static final String SUPER_ADMIN = "super_admin";

    /** 部门请求头（网关暂未注入，预留给后续版本） */
    public static final String DEPT_ID_HEADER = "X-Dept-Id";

    /**
     * 从当前请求解析操作者。
     *
     * <p>权限集合取「权限码头 ∪ 角色头」：版块按角色可见（{@code visibleRoles}）时
     * 比对的是角色码，只取权限码头会永远判不中，表现为「有权限却看不到版块」。</p>
     *
     * @param request 当前请求
     * @return 操作者
     */
    public static ForumActor from(HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        Set<String> codes = new LinkedHashSet<>(UserContext.permissionCodes(request));
        String roles = WebHeaders.header(request, WebHeaders.USER_ROLES);
        if (roles != null) {
            for (String role : roles.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    codes.add(trimmed);
                }
            }
        }
        return new ForumActor(userId, codes, WebHeaders.header(request, DEPT_ID_HEADER));
    }

    /**
     * 判断是否具备某权限码（超管恒真）。
     *
     * @param code 权限码
     * @return 具备时返回 {@code true}
     */
    public boolean hasPermission(String code) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains(SUPER_ADMIN) || permissions.contains(code);
    }

    /**
     * 判断是否具备任一权限码。
     *
     * @param codes 权限码集合
     * @return 命中任一即返回 {@code true}
     */
    public boolean hasAnyPermission(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        for (String code : codes) {
            if (hasPermission(code)) {
                return true;
            }
        }
        return false;
    }
}
