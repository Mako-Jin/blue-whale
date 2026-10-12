package com.bluewhale.sts.auth.common;

import java.util.List;

/**
 * 权限标识常量。
 *
 * <p>权限标识（即资源标识）是**注解与种子数据、后台配置三处都要用到的同一个字符串**。
 * 散着写成字面量，一旦有一处拼错（{@code auth:user:ceate}），
 * 症状是"注解写了、后台也配了，但就是不生效"——两边都以为自己写对了。
 * 收成常量后，拼写错误在编译期就不存在。
 *
 * <p>命名格式统一为 {@code 域:资源:动作}，全小写、冒号分隔。
 * 它同时也是后台人员配置权限时看到的标识，因此要**稳定**：
 * 改一个标识等于改所有引用它的注解与后台配置。
 */
public final class AuthPermissions {

    private AuthPermissions() {
    }

    public static final String TENANT_READ = "auth:tenant:read";
    public static final String TENANT_CREATE = "auth:tenant:create";
    public static final String TENANT_UPDATE = "auth:tenant:update";
    public static final String TENANT_DELETE = "auth:tenant:delete";

    public static final String USER_READ = "auth:user:read";
    public static final String USER_CREATE = "auth:user:create";
    public static final String USER_UPDATE = "auth:user:update";
    public static final String USER_DELETE = "auth:user:delete";

    /**
     * 资源目录的增删改查。
     *
     * <p><b>请注意它是自指的</b>：这四个权限保护的正是"登记权限"这件事本身 ——
     * 有了 {@code auth:resource:create} 才能新建别的权限。自指不是缺陷，而是
     * 引导链的必然：第一个权限只能来自种子数据（见 {@code DefaultAdminSeeder}），
     * 之后的权限都由持有 {@code auth:resource:*} 的人登记。
     *
     * <p>它是"把权限配置从改库变成点界面"的前提。在此之前，往
     * {@code bw_auth_resource} 里加一条只能靠种子或直接写 SQL，而资源标识
     * （即权限码）是**长期契约**，用 SQL 维护它需要人肉记住格式约束 ——
     * 那正是 {@code AuthTaxonomyTest} 想拦住的错误。
     */
    public static final String RESOURCE_READ = "auth:resource:read";
    public static final String RESOURCE_CREATE = "auth:resource:create";
    public static final String RESOURCE_UPDATE = "auth:resource:update";
    public static final String RESOURCE_DELETE = "auth:resource:delete";

    /**
     * 角色的增删改查。
     *
     * <p><b>授权（把资源授给角色）刻意共用 {@code auth:role:update}，不单设
     * {@code auth:role:grant}。</b>依据是本仓已有的一条同类判断：重置口令共用
     * {@code auth:user:update}（见 {@code AuthAuditActions.USER_RESET_PASSWORD}），
     * 理由是"能改这个对象的人本来就能改它的全部方面"。角色也一样 ——
     * 角色的授权集合就是角色的一部分，把"改角色名"与"改角色权限"分给不同的人，
     * 当前没有这样的用例。
     *
     * <p>但这条与重置口令有一处**性质上的差别**，值得写下来：给自己持有的角色授权
     * 等于给自己提权。所以如果将来出现"只让某人改角色描述、但不许他动权限"的诉求，
     * 那就是这里该拆成两个权限码的**明确触发条件**（拆分是加一个常量 + 换注解，
     * 不影响数据）；在那之前不拆，避免用权限码表达一个不存在的分工。
     */
    public static final String ROLE_READ = "auth:role:read";
    public static final String ROLE_CREATE = "auth:role:create";
    public static final String ROLE_UPDATE = "auth:role:update";
    public static final String ROLE_DELETE = "auth:role:delete";

    /**
     * 查看审计流水。
     *
     * <p><b>只有 read，没有对应的 write / delete —— 这不是还没做。</b>
     * 审计记录由后端拦截器自动产生，本仓刻意**不提供任何写入或修改审计的接口**
     * （见 {@code AuditLogController} 的注释）。因此"不可篡改"这件事在这里
     * 不靠权限保护，靠接口不存在：没有一个"改审计"的接口可以授予谁。
     */
    public static final String AUDIT_READ = "auth:audit:read";

    /** 本切片已落地的全部权限标识，种子数据据此建资源 */
    public static final List<String> ALL = List.of(
            TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE,
            USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE,
            RESOURCE_READ, RESOURCE_CREATE, RESOURCE_UPDATE, RESOURCE_DELETE,
            ROLE_READ, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE,
            AUDIT_READ);

}
