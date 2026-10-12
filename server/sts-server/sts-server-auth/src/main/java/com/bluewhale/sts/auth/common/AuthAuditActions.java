package com.bluewhale.sts.auth.common;

/**
 * 权限域的动作标识常量 —— 与 {@link AuthPermissions}（资源标识）配套使用。
 *
 * <p>形状是 {@code 域.资源.动作} 三段（{@code auth.user.create}），与权限码
 * {@code auth:user:create} **同构**，只把冒号换成点。这不是巧合，而是刻意的：
 * 两者描述的本来就是同一件事的两种用途 —— 权限码回答"谁被允许做"，
 * 动作标识回答"实际做了什么"。形状对齐之后，"某个权限对应的操作被调用了几次"
 * 这类问题在审计表里可以直接按前缀匹配，不需要维护一张对照表。
 *
 * <p><b>为什么必须自带"域"这一段（不能写成 {@code user.create}）：</b>
 * 动作标识是**永久契约**（见 {@code @Audited} 的注释），一旦落库就不能改名。
 * 如果它不自带归属，"权限域的 user.create"与"将来某个业务域的 user.create"
 * 就会在审计表里撞成同一个值，而区分它们要靠另一列 {@code resource} ——
 * 也就是说，任何按动作过滤的查询都必须记得同时过滤资源，忘一次就得到一个
 * 混了两个域的错误结果，而且不报错。给契约加一段前缀，把这个坑从"必须记得"
 * 变成"不可能发生"。
 *
 * <p><b>为什么单独建一个类而不是塞进 {@link AuthPermissions}：</b>
 * 两者数量不成一一对应 —— {@code auth:user:read} 没有对应的审计动作（查询不记审计），
 * 而 {@code auth.user.reset_password} 对应的是 {@code auth:user:update} 权限。
 * 混在一个类里，看代码的人会以为它们该一一对应，然后花时间去"补齐"缺失的那几个。
 */
public final class AuthAuditActions {

    private AuthAuditActions() {
    }

    public static final String TENANT_CREATE = "auth.tenant.create";
    public static final String TENANT_UPDATE = "auth.tenant.update";
    public static final String TENANT_DELETE = "auth.tenant.delete";

    public static final String USER_CREATE = "auth.user.create";
    public static final String USER_UPDATE = "auth.user.update";
    public static final String USER_DELETE = "auth.user.delete";

    /**
     * 资源目录的增删改。
     *
     * <p><b>查询没有对应的动作标识</b>，与用户 / 租户一致：查询不记审计。
     *
     * <p>这三个动作值得单独留痕的理由比别处更强：改一条资源等于改一个**权限码**，
     * 而权限码是长期契约 —— 一次改名可能让某些角色的授权静默失效（资源被改了码，
     * 角色持有的还是旧码）。审计里能查到"谁在什么时候把 {@code auth:user:create}
     * 改成了别的"，是排查"为什么这个角色的权限突然不对了"的唯一线索。
     */
    public static final String RESOURCE_CREATE = "auth.resource.create";
    public static final String RESOURCE_UPDATE = "auth.resource.update";
    public static final String RESOURCE_DELETE = "auth.resource.delete";

    /**
     * 角色的增删改，以及**授权与撤权**。
     *
     * <p><b>授权与撤权是两个独立动作，不是"角色改了"</b> —— 虽然它们共用
     * {@code auth:role:update} 权限（理由见 {@link AuthPermissions#ROLE_UPDATE}）。
     * 这正是本类注释开头那段话的实例：权限码与动作标识**数量上不一一对应**，
     * 因为两者回答的问题不同。事后追责时"谁给这个角色加了 {@code auth:role:delete}"
     * 是一个必须能直接过滤出来的问题，把它混进 {@code auth.role.update} 里
     * 就只能靠比对前后快照，而审计面板只存了变更后的值。
     *
     * <p>这也解释了为什么{@code resource} 填 {@code auth:role} 而不是被授的那个资源：
     * 变化的是角色的授权集合，资源本身没动。按资源查审计的入口因此查不到授权的历史 ——
     * 这是刻意的取舍：一条审计只有一个归属，而"谁动了这个角色"比"谁动过这个资源"
     * 更接近排查时的第一个问题。
     */
    public static final String ROLE_CREATE = "auth.role.create";
    public static final String ROLE_UPDATE = "auth.role.update";
    public static final String ROLE_DELETE = "auth.role.delete";
    public static final String ROLE_GRANT = "auth.role.grant";
    public static final String ROLE_REVOKE = "auth.role.revoke";

    /**
     * 给角色增减成员（用户 / 用户组）—— 与上面两个"给角色增减资源"**必须分开**。
     *
     * <p>两者共用 {@code auth:role:update} 权限码，但动作完全不同，混在一起的后果比
     * 上面那段注释说的更严重：{@code auth.role.grant} 回答的是"谁扩大了这个角色的权限范围"，
     * 而这里回答的是"谁给了某个人这个角色"。后者是**更直接**的一次提权 ——
     * 把资源授给角色，你得先持有那个角色才谈得上受益；把角色授给一个人，
     * 受益者当下就换了。事后追责时"谁把 SUPER_ADMIN 授给了这个账号"是一个必须能
     * 一条过滤直接命中的问题，让它藏在 {@code auth.role.grant} 里等于没记录。
     *
     * <p>命名沿用 {@code auth.user.reset_password} 的"动词_宾语"形状，
     * 与 {@code auth.role.grant} / {@code auth.role.revoke} 靠宾语区分。
     */
    public static final String ROLE_MEMBER_GRANT = "auth.role.grant_member";
    public static final String ROLE_MEMBER_REVOKE = "auth.role.revoke_member";

    /**
     * 用户组的增删改，以及**给用户组增减成员**。
     *
     * <p><b>为什么用户组需要一组独立的动作标识，而不是蹭 {@code auth.user.*}：</b>
     * 用户组是**权限的载体**（它本身可以被授予角色），不是"用户的一个属性"。
     * 事后追责时"谁把 300 个人塞进了这个组"与"谁改了某个用户的资料"是两条完全不同的线索，
     * 而前者只体现为一次成员关系的写入 —— 揉进 {@code auth.user.update} 就永远查不出来。
     *
     * <p>成员动作用 {@code grant_member} / {@code revoke_member}，与角色侧**同名形状**：
     * 两处都在"给一个权限载体增加 / 移除参与者"，靠 {@code resource} 段区分载体是谁。
     * 这个一致性是有用的 —— 审计面板按动作名过滤时，一次就能同时看到两类载体的变动。
     *
     * <p>{@code USER_GROUP_MEMBER_GRANT} 是本域里**第二条间接提权路径**：
     * 把用户加进一个已被授予角色的组，用户当下就多了一批权限，
     * 而没有任何一条记录写着"谁给这个用户提了权"。它必须能一条过滤直接命中。
     */
    public static final String USER_GROUP_CREATE = "auth.user_group.create";
    public static final String USER_GROUP_UPDATE = "auth.user_group.update";
    public static final String USER_GROUP_DELETE = "auth.user_group.delete";
    public static final String USER_GROUP_MEMBER_GRANT = "auth.user_group.grant_member";
    public static final String USER_GROUP_MEMBER_REVOKE = "auth.user_group.revoke_member";

    /**
     * 重置口令。
     *
     * <p>与 {@code user.update} 分开记而不是合并：重置口令是运维/管理员介入他人账号的动作，
     * 事后追责时"谁重置过这个账号的口令"与"谁改过这个账号的资料"是两个不同的问题。
     * 权限上两者共用 {@code auth:user:update}（能改资料的人本来就能重置口令），
     * 但审计上必须分得开 —— 这正是"动作标识"不能直接复用权限码的原因。
     */
    public static final String USER_RESET_PASSWORD = "auth.user.reset_password";

}
