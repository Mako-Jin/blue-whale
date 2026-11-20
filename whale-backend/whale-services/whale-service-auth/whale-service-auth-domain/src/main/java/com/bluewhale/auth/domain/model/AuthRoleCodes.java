package com.bluewhale.auth.domain.model;

import java.util.List;
import java.util.Set;

/**
 * 认证域内置角色编码字典：应用层（超管全量放权计算权限码）、领域层（租户开通默认角色）
 * 与基础设施层（{@code @RequirePermission} 拦截器超管放行）共用同一字符串来源，避免魔法串。
 *
 * <p>内置角色是领域概念（聚合创建时引用的固定编码），因此字典收纳于 {@code domain.model}；
 * 后续新增内置角色在此扩充。</p>
 *
 * <p><b>三员分立（等保 2.0）</b>：系统管理员 {@link #SYSTEM_ADMIN}、安全管理员
 * {@link #SECURITY_ADMIN}、安全审计员 {@link #AUDIT_ADMIN} 构成职责分离的三员角色，
 * 三者<b>两两互斥</b>（同一用户最多持有一个），且权限集由内置矩阵固定，不允许在管理端
 * 手工调整——否则「管理员给自己加审计权限」即可绕过职责分离。对应的角色播种、授权与
 * 互斥约束播种见 {@code V36__three_admin_segregation.sql}。</p>
 *
 * @author BlueWhale
 */
public final class AuthRoleCodes {

    /** 内置超级管理员 */
    public static final String SUPER_ADMIN = "super_admin";

    /** 内置租户管理员：新租户开通时的默认管理员角色编码 */
    public static final String TENANT_ADMIN = "tenant_admin";

    /** 内置系统管理员（三员之一）：负责用户、组织、菜单、任务等系统运维，不含安全策略与审计 */
    public static final String SYSTEM_ADMIN = "system_admin";

    /** 内置安全管理员（三员之一）：负责角色授权、权限模型、口令/会话策略、密钥与网关安全 */
    public static final String SECURITY_ADMIN = "security_admin";

    /** 内置安全审计员（三员之一）：只读查询与导出审计记录，不含任何业务与管理写权限 */
    public static final String AUDIT_ADMIN = "audit_admin";

    /**
     * 三员角色编码（固定顺序：系统管理员 → 安全管理员 → 安全审计员）。
     *
     * <p>顺序与 {@code V36} 迁移里的角色排序号一致，前端按此顺序展示；互斥校验不依赖顺序。</p>
     */
    public static final List<String> THREE_ADMIN_CODES = List.of(SYSTEM_ADMIN, SECURITY_ADMIN, AUDIT_ADMIN);

    /**
     * 内置角色编码全集：不可删除、不可改名。
     *
     * <p>{@code super_admin} 是交付初始化账号角色，其余四个是租户级内置角色。
     * 内置判定只认编码，不依赖 {@code sys_role.category}——category 可被历史数据写成
     * GENERAL，靠它判断会把保护做成摆设。</p>
     */
    private static final Set<String> BUILT_IN_CODES =
            Set.of(SUPER_ADMIN, TENANT_ADMIN, SYSTEM_ADMIN, SECURITY_ADMIN, AUDIT_ADMIN);

    /**
     * 私有构造器：常量类禁止实例化
     */
    private AuthRoleCodes() {
    }

    /**
     * 判断角色编码是否属于三员之一
     *
     * @param roleCode 角色编码（可空）
     * @return 属于系统管理员 / 安全管理员 / 安全审计员时返回 {@code true}
     */
    public static boolean isThreeAdmin(String roleCode) {
        return roleCode != null && THREE_ADMIN_CODES.contains(roleCode);
    }

    /**
     * 判断角色编码是否为内置角色
     *
     * @param roleCode 角色编码（可空）
     * @return 内置角色返回 {@code true}
     */
    public static boolean isBuiltIn(String roleCode) {
        return roleCode != null && BUILT_IN_CODES.contains(roleCode);
    }
}
