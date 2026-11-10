package com.bluewhale.auth.application.model;

/**
 * 认证域内置角色编码字典：应用层（超管全量放权计算权限码）与基础设施层
 * （{@code @RequirePermission} 拦截器超管放行）共用同一字符串来源，避免魔法串。
 *
 * <p>当前内置角色仅超级管理员；后续新增内置角色（如租户管理员）在此扩充。</p>
 *
 * @author BlueWhale
 */
public final class AuthRoleCodes {

    /** 内置超级管理员 */
    public static final String SUPER_ADMIN = "super_admin";

    /**
     * 私有构造器：常量类禁止实例化
     */
    private AuthRoleCodes() {
    }
}
