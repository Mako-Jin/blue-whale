package com.bluewhale.auth.domain.model;

/**
 * 认证域内置角色编码字典：应用层（超管全量放权计算权限码）、领域层（租户开通默认角色）
 * 与基础设施层（{@code @RequirePermission} 拦截器超管放行）共用同一字符串来源，避免魔法串。
 *
 * <p>内置角色是领域概念（聚合创建时引用的固定编码），因此字典收纳于 {@code domain.model}；
 * 后续新增内置角色在此扩充。</p>
 *
 * @author BlueWhale
 */
public final class AuthRoleCodes {

    /** 内置超级管理员 */
    public static final String SUPER_ADMIN = "super_admin";

    /** 内置租户管理员：新租户开通时的默认管理员角色编码 */
    public static final String TENANT_ADMIN = "tenant_admin";

    /**
     * 私有构造器：常量类禁止实例化
     */
    private AuthRoleCodes() {
    }
}
