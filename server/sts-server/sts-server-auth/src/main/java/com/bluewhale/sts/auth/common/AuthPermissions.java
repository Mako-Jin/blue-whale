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
     * 组织（部门）的增删改查，以及**移动节点**与**组织成员 / 主岗**。
     *
     * <p><b>资源段是 {@code org} 而不是 {@code organization}</b> —— 表名是
     * {@code bw_auth_organization}，这里取短形，与路径 {@code /api/auth/orgs} 一致。
     * 两种写法都符合 {@code AuthTaxonomyTest} 钉死的字符集，选短的那个是因为它要
     * 出现在权限码、审计动作、前端常量三处，每次多打 8 个字符不会带来任何清晰度。
     *
     * <p><b>移动节点没有独立的权限码，但有自己的审计动作。</b>移动一棵子树会连带改变
     * 整棵子树上**每一个人的 DEPT / DEPT_AND_CHILD 数据范围**（它们是按路径前缀求值的），
     * 也就是说这是一次能改变很多人可见范围的配置变更。权限上它属于"改这个组织"
     * （{@code auth:org:update}），但审计上必须能单独过滤出来，
     * 见 {@code AuthAuditActions.ORG_MOVE} —— 与角色侧"授权共用 update 权限、
     * 但 {@code auth.role.grant} 独立"是完全同一条判断。
     *
     * <p><b>成员与主岗同样共用 {@code auth:org:update}。</b>{@code bw_auth_rel_org_user}
     * 是组织的内部边（不存在"没有组织的组织归属"），与用户组的成员关系同构。
     * 但其中**设主岗**要特别说一句：主岗决定这个人的 DEPT 落在哪个部门，
     * 所以"把某人的主岗改到另一个部门"是**直接改数据范围**的一次操作，
     * 它有自己的审计动作（见 {@code AuthAuditActions.ORG_SET_PRIMARY_POSITION}）。
     */
    public static final String ORG_READ = "auth:org:read";
    public static final String ORG_CREATE = "auth:org:create";
    public static final String ORG_UPDATE = "auth:org:update";
    public static final String ORG_DELETE = "auth:org:delete";

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
     * 用户组的增删改查。
     *
     * <p><b>为什么资源段用下划线而不是连字符</b>（路径 {@code /api/auth/user-groups}、
     * 标识 {@code auth:user_group:read}）：三套标识的字符集由 {@code AuthTaxonomyTest}
     * 钉死为 {@code [a-z][a-z0-9_]*}。URL 给人看、标识给程序与后台配置用，
     * 后者放宽字符集会让"用标识当键"的地方处处需要转义 —— 见
     * {@link AuthResources#USER_GROUP}。
     *
     * <p><b>成员关系共用 {@code auth:user_group:update}</b>，与角色侧完全同构：
     * 组成员是用户组的**内部边**，不存在"没有用户组的成员关系"。
     * 但审计动作必须分开，见 {@code AuthAuditActions.USER_GROUP_MEMBER_GRANT} ——
     * 把一个用户加进"已被授予角色"的组，等于一次**间接提权**，
     * 事后追责时这是与"改用户组名"完全不同的问题。
     *
     * <p>与角色一样，这里没有单设 {@code auth:user_group:member}：
     * 当前只有一个身份持有 {@code auth:user_group:update}，拆出来会是一个
     * 没人能"不同地持有"的权限码。触发条件同 {@link #ROLE_UPDATE} 的注释。
     */
    public static final String USER_GROUP_READ = "auth:user_group:read";
    public static final String USER_GROUP_CREATE = "auth:user_group:create";
    public static final String USER_GROUP_UPDATE = "auth:user_group:update";
    public static final String USER_GROUP_DELETE = "auth:user_group:delete";

    /**
     * 查看审计流水。
     *
     * <p><b>只有 read，没有对应的 write / delete —— 这不是还没做。</b>
     * 审计记录由后端拦截器自动产生，本仓刻意**不提供任何写入或修改审计的接口**
     * （见 {@code AuditLogController} 的注释）。因此"不可篡改"这件事在这里
     * 不靠权限保护，靠接口不存在：没有一个"改审计"的接口可以授予谁。
     */
    public static final String AUDIT_READ = "auth:audit:read";

    /**
     * 查看客户端技术日志。
     *
     * <p><b>为什么这里出现了 {@code log:} 这个全新的域前缀：</b>本类是**全仓权限标识的
     * 唯一登记处**，不只是 {@code auth} 域的常量集合 —— 它是种子数据的唯一来源
     * （{@code bw_auth_resource} 的每一行都由 {@link #ALL} 建出来），
     * 因此凡是要经过 {@code @RequiresPermission} 判定的接口，其权限码都必须登记在这里，
     * 没有第二个地方可放。域前缀跟着**被访问的对象**走，不跟着代码所在的模块走。
     *
     * <p><b>与技术日志"上报"接口无关（重要）：</b>上报接口（{@code POST /api/logs/client}）
     * 保持**公开**，见 {@code ClientLogController} 的说明 —— 登录页出错时也必须能上报。
     * 本权限码只约束**查询**：读取日志意味着读到别人的报错正文、上下文与 IP。
     */
    public static final String LOG_CLIENT_READ = "log:client:read";

    /**
     * 查看客户端行为事件。
     *
     * <p><b>为什么与 {@link #AUDIT_READ} 分开、也与 {@link #LOG_CLIENT_READ} 分开：</b>
     * 三者是三条不同的管道、三种不同的读者。审计问的是"谁在何时对哪个资源做了什么"，
     * 读者是合规与追责；技术日志问的是"这段代码为什么出错"，读者是排障；
     * 行为事件问的是"用户做了什么操作"，读者是产品与运营。
     * 合成一个码的后果是**一次授权把三种数据一起给出去** —— 而它们的内容敏感度
     * 与保留期都不同，事后想收窄只能靠改后台配置，改不动就得改代码。
     */
    public static final String EVENT_CLIENT_READ = "event:client:read";

    /** 每个应用入口一条：这条码决定基座导航栏上是否出现它。 */
    public static final String UI_LOG_CONSOLE_VIEW = "ui:log_console:view";
    public static final String UI_EVENT_CONSOLE_VIEW = "ui:event_console:view";
    public static final String UI_ENVIRONMENT_VIEW = "ui:environment:view";
    public static final String UI_ADMIN_VIEW = "ui:admin:view";
    public static final String UI_AUDIT_CONSOLE_VIEW = "ui:audit_console:view";

    /** 日志台内部两个视图：演示区与查询区。 */
    public static final String UI_LOG_REPORTER_VIEW = "ui:log_reporter:view";
    public static final String UI_LOG_QUERY_VIEW = "ui:log_query:view";

    /** 事件台内部两个视图：埋点演示区与查询区。 */
    public static final String UI_EVENT_TRACK_VIEW = "ui:event_track:view";
    public static final String UI_EVENT_QUERY_VIEW = "ui:event_query:view";

    /** 权限管理内部六个标签页。 */
    public static final String UI_ADMIN_USER_VIEW = "ui:admin_user:view";
    public static final String UI_ADMIN_TENANT_VIEW = "ui:admin_tenant:view";
    public static final String UI_ADMIN_RESOURCE_VIEW = "ui:admin_resource:view";
    public static final String UI_ADMIN_ROLE_VIEW = "ui:admin_role:view";
    public static final String UI_ADMIN_USER_GROUP_VIEW = "ui:admin_user_group:view";
    public static final String UI_ADMIN_ORGANIZATION_VIEW = "ui:admin_organization:view";

    /** 审计台内部三块：流水、按动作聚合、演练区。 */
    public static final String UI_AUDIT_FLOW_VIEW = "ui:audit_flow:view";
    public static final String UI_AUDIT_STATS_VIEW = "ui:audit_stats:view";
    public static final String UI_AUDIT_DRILL_VIEW = "ui:audit_drill:view";

    /** 本切片已落地的全部权限标识，种子数据据此建资源 */
    public static final List<String> ALL = List.of(
            TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE,
            USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE,
            ORG_READ, ORG_CREATE, ORG_UPDATE, ORG_DELETE,
            RESOURCE_READ, RESOURCE_CREATE, RESOURCE_UPDATE, RESOURCE_DELETE,
            ROLE_READ, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE,
            USER_GROUP_READ, USER_GROUP_CREATE, USER_GROUP_UPDATE, USER_GROUP_DELETE,
            AUDIT_READ,
            LOG_CLIENT_READ, EVENT_CLIENT_READ,
            UI_LOG_CONSOLE_VIEW, UI_EVENT_CONSOLE_VIEW, UI_ENVIRONMENT_VIEW,
            UI_ADMIN_VIEW, UI_AUDIT_CONSOLE_VIEW,
            UI_LOG_REPORTER_VIEW, UI_LOG_QUERY_VIEW,
            UI_EVENT_TRACK_VIEW, UI_EVENT_QUERY_VIEW,
            UI_ADMIN_USER_VIEW, UI_ADMIN_TENANT_VIEW, UI_ADMIN_RESOURCE_VIEW,
            UI_ADMIN_ROLE_VIEW, UI_ADMIN_USER_GROUP_VIEW, UI_ADMIN_ORGANIZATION_VIEW,
            UI_AUDIT_FLOW_VIEW, UI_AUDIT_STATS_VIEW, UI_AUDIT_DRILL_VIEW);

}
