package com.bluewhale.overview;

import java.util.Arrays;
import java.util.Set;

/**
 * 概览构建上下文：调用者身份、权限码与角色码。
 *
 * <p><b>为什么要把权限码显式传进 provider，而不是让 provider 自己去读请求头：</b>
 * provider 在独立线程里执行（超时要能中断），拿不到请求线程的
 * {@code RequestContextHolder}。先快照再传值，是唯一不会在异步化之后悄悄失效的写法。</p>
 *
 * <p><b>为什么要带上角色码。</b>权限码回答"能不能看这一屏"，角色码回答"这一屏的数字
 * 该是多大范围"。CRM 的销售看板按角色划数据范围（{@code tenant_admin / crm_admin /
 * crm_sales_manager / crm_analyst} 看全量，其余只看本人），这个判定发生在
 * {@code CrmActor#allDataScope()} 里，只看权限码永远得不出结论。少了角色码，城里
 * 只能按"本人范围"算——管理员站在礼部坊前看到的是一片 0，而 2D 后台同一时刻显示的是
 * 全租户业绩。两处口径分叉比缺一块牌更糟，所以角色必须与权限一起快照。</p>
 *
 * @param userId 调用者用户 ID，可空
 * @param tenantId 租户 ID，可空
 * @param permissionCodes 调用者权限码集合（网关注入口径），不空
 * @param roleCodes 调用者角色码集合（网关注入口径），不空
 * @author BlueWhale
 */
public record OverviewContext(Long userId, Long tenantId, Set<String> permissionCodes, Set<String> roleCodes) {

    /** 内置超管角色码，拥有全部权限（与 {@code RequirePermissionInterceptor} 同口径） */
    public static final String SUPER_ADMIN = "super_admin";

    /**
     * 规范化：权限与角色集合为空时转空集
     *
     * <p>保留三参重载：绝大多数 provider 只看权限码，加角色是给需要做数据范围判定的
     * 服务用的，不该逼着十一个殿的调用点一起改签名。</p>
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCodes 权限码，可空
     * @return 上下文（角色集合为空）
     */
    public static OverviewContext of(Long userId, Long tenantId, Set<String> permissionCodes) {
        return of(userId, tenantId, permissionCodes, null);
    }

    /**
     * 规范化：权限与角色集合为空时转空集
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCodes 权限码，可空
     * @param roleCodes 角色码，可空
     * @return 上下文
     */
    public static OverviewContext of(Long userId, Long tenantId, Set<String> permissionCodes, Set<String> roleCodes) {
        return new OverviewContext(userId, tenantId,
                permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes),
                roleCodes == null ? Set.of() : Set.copyOf(roleCodes));
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

    /**
     * 是否拥有任意指定角色（超管放行）
     *
     * <p>只读判定，不下发角色名。超管一并放行，与 {@link #hasPermission} 同口径，
     * 用于"要不要显示这一块"的可见性判断。<b>数据范围不要用它替代业务规则</b>：
     * 谁看全量、谁看本人，是各服务自己的事（例如 CRM 的 {@code CrmActor}），
     * 城里必须与 2D 给出同一个数，而不是另立一套。</p>
     *
     * @param codes 角色码，可空
     * @return 命中任意角色或超管时返回 {@code true}
     */
    public boolean hasRole(String... codes) {
        if (codes == null || codes.length == 0) {
            return false;
        }
        if (roleCodes.contains(SUPER_ADMIN)) {
            return true;
        }
        return Arrays.stream(codes).anyMatch(code -> code != null && roleCodes.contains(code));
    }
}
