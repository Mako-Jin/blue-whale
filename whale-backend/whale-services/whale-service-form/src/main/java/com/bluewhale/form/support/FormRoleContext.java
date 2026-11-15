package com.bluewhale.form.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前请求的用户角色上下文（线程级）。
 *
 * <p>由 {@code FormTenantConfig} 的拦截器从网关可信请求头 {@code X-User-Roles} 写入，
 * 请求结束后清理，供字段级权限判定读取。使用线程级上下文而非逐个服务方法传参，
 * 避免为字段级权限扩散修改所有服务签名。</p>
 *
 * <p><b>边界</b>：角色集合为空表示「未携带角色信息」（如服务间内网调用），
 * 此时不做字段裁剪与限制，与升级前行为一致；字段级权限依赖网关覆盖注入角色头。</p>
 *
 * @author BlueWhale
 */
public final class FormRoleContext {

    /** 当前线程角色集合（不可变，空集合表示未携带） */
    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();

    /** 工具类不允许实例化 */
    private FormRoleContext() {
    }

    /**
     * 读取当前线程角色集合
     *
     * @return 角色集合；未设置时返回空集合
     */
    public static Set<String> get() {
        Set<String> roles = ROLES.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    /**
     * 设置当前线程角色集合
     *
     * @param roles 角色集合（可空，空集合等价于未携带）
     */
    public static void set(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            ROLES.remove();
            return;
        }
        ROLES.set(Collections.unmodifiableSet(new LinkedHashSet<>(roles)));
    }

    /**
     * 清理当前线程角色集合（请求结束必须调用，避免线程复用串号）
     */
    public static void clear() {
        ROLES.remove();
    }
}
