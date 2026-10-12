package com.bluewhale.sts.auth.common;

/**
 * 权限域的资源标识常量 —— 与 {@link AuthPermissions}（权限码）、
 * {@link AuthAuditActions}（动作标识）配套的第三个概念。
 *
 * <p><b>三者不是一回事，别混：</b>
 * <pre>
 *   资源标识   auth:user                 ← 本类。"这个对象是什么"
 *   权限码     auth:user:create          ← AuthPermissions。"谁被允许做什么"
 *   动作标识   auth.user.create          ← AuthAuditActions。"实际发生了什么"
 * </pre>
 *
 * <p>形状上，权限码 = 资源标识 + {@code :动作}（把动作部分替换成冒号分隔的动词），
 * 但**资源标识与动作标识不是简单的字符串变换关系**：重置口令的权限码是
 * {@code auth:user:update}（能改资料的人本来就能重置口令），而它的动作标识是
 * {@code auth.user.reset_password}（事后追责时这是完全不同的问题）。
 * 因此三者各自独立声明，靠约定的形状对齐，而不是靠运行时拼接 ——
 * 拼接出来的东西改一次拼法就会静默对不上，而这三样都是长期契约。
 *
 * <p><b>为什么要有这个类：</b>写 {@code @Audited} 时最容易犯的错就是把
 * {@code resource} 填成权限码（毕竟两者都在 {@link AuthPermissions} 里，
 * 顺手就填了）。填错不会报错，只会让"按资源过滤审计"这个查询**静默地查不到任何东西** ——
 * 而它正是审计面板最常用的入口之一。给它一个自己的常量类，让正确写法成为最短的那条路。
 */
public final class AuthResources {

    private AuthResources() {
    }

    public static final String TENANT = "auth:tenant";

    public static final String USER = "auth:user";

    /**
     * 资源目录。
     *
     * <p>它对应 {@code auth:resource:*} 四个权限码，形状上与 {@link #TENANT} / {@link #USER}
     * 完全同类。它出现在 {@code @Audited(resource = ...)} 里，让"谁改过权限配置"
     * 在审计面板里可以按资源过滤出来 —— 这是权限系统自身最需要留痕的一类操作。
     *
     * <p><b>别把它与被登记的资源混起来</b>：{@code bw_auth_resource} 表里存的是
     * **权限码**（{@code auth:user:create}），本常量是**资源标识**（{@code auth:resource}）。
     * 表里若真出现一行 {@code auth:resource}，那是错把资源标识当权限码登记了 ——
     * 它的形状只有两段，不符合 {@code AuthTaxonomyTest} 钉死的三段式。
     */
    public static final String RESOURCE = "auth:resource";

    /**
     * 审计资源。
     *
     * <p>它对应 {@link AuthPermissions#AUDIT_READ}，虽然当前**没有任何写动作**挂在它下面
     * （审计域刻意不提供写入接口，见 {@code AuditLogController}），
     * 但这个资源客观存在 —— 权限码是 {@code auth:audit:read}，那"域:资源"两段就是
     * {@code auth:audit}。{@code AuthTaxonomyTest} 会强制"每个权限码都能归到某个
     * 已声明的资源下"，这条断言正是靠本常量成立的。
     *
     * <p>也正因为没有写动作，它永远不会出现在 {@code @Audited(resource = ...)} 里 ——
     * 这不算"没用到的常量"，而是一条分类学事实。
     */
    public static final String AUDIT = "auth:audit";

}
