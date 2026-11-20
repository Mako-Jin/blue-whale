package com.bluewhale.overview;

import java.util.Set;

/**
 * 概览构建上下文：调用者身份与权限码。
 *
 * <p><b>为什么要把权限码显式传进 provider，而不是让 provider 自己去读请求头：</b>
 * provider 在独立线程里执行（超时要能中断），拿不到请求线程的
 * {@code RequestContextHolder}。先快照再传值，是唯一不会在异步化之后悄悄失效的写法。</p>
 *
 * @param userId 调用者用户 ID，可空
 * @param tenantId 租户 ID，可空
 * @param permissionCodes 调用者权限码集合（网关注入口径），不空
 * @author BlueWhale
 */
public record OverviewContext(Long userId, Long tenantId, Set<String> permissionCodes) {

    /** 内置超管角色码，拥有全部权限（与 {@code RequirePermissionInterceptor} 同口径） */
    public static final String SUPER_ADMIN = "super_admin";

    /**
     * 规范化：权限集合为空时转空集
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCodes 权限码，可空
     * @return 上下文
     */
    public static OverviewContext of(Long userId, Long tenantId, Set<String> permissionCodes) {
        return new OverviewContext(userId, tenantId, permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes));
    }

    /**
     * 是否具备指定权限码（超管放行）
     *
     * <p>用于块级裁剪：没有 {@code sys:erp:finance:view} 的人不该在厅里看到财务块，
     * 也不该在 JSON 里看到它 —— 下发再让前端隐藏等于把数据白送。</p>
     *
     * @param code 权限码；空白视为不设限
     * @return 具备或未设限时返回 {@code true}
     */
    public boolean hasPermission(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        return permissionCodes.contains(SUPER_ADMIN) || permissionCodes.contains(code);
    }
}
