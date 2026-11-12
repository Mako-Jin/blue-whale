package com.bluewhale.forum.common;

/**
 * 论坛权限码常量（号段 2340–2350，见规格 v1.3 §八；原 2330 段与 whale-assistant 冲突已后移）。
 *
 * <p>权限码必须与 {@code migration-6.43-forum.sql} 中的 {@code sys_permission.perm_code}
 * 逐字一致：授权语句按 {@code perm_code} 授权，不按 ID 区间——同名权限码在存量库里
 * 可能挂着另一个 ID，按区间授权必然漏授且不报错。</p>
 *
 * <p>新增权限点的顺序：先改本类 → 再补迁移 → 最后改接口注解，三步缺一即为
 * 「代码里有、库里没有」的静默失效。</p>
 *
 * @author BlueWhale
 */
public final class ForumPermissions {

    /** 浏览论坛（版块树 / 帖子列表 / 详情） */
    public static final String BROWSE = "sys:forum:browse";

    /** 发帖 */
    public static final String THREAD_ADD = "sys:forum:thread:add";

    /** 编辑本人帖（服务端仍校验所有权与编辑时间窗） */
    public static final String THREAD_EDIT = "sys:forum:thread:edit";

    /** 删除本人帖 */
    public static final String THREAD_REMOVE = "sys:forum:thread:remove";

    /** 回帖 */
    public static final String POST_ADD = "sys:forum:post:add";

    /** 投花 / 藏帖 */
    public static final String REACTION_USE = "sys:forum:reaction:use";

    /** 管理端入口（待审 / 举报 / 敏感词） */
    public static final String MANAGE_LIST = "sys:forum:manage:list";

    /** 审核（通过 / 驳回） */
    public static final String MANAGE_AUDIT = "sys:forum:manage:audit";

    /** 悬榜 / 题金榜 / 关闭 / 处置 */
    public static final String MANAGE_DISPOSE = "sys:forum:manage:dispose";

    /** 版块管理（含审核开关与可见性） */
    public static final String MANAGE_BOARD = "sys:forum:manage:board";

    /** 热榜手工刷新 */
    public static final String HOT_REFRESH = "sys:forum:hot:refresh";

    private ForumPermissions() {
    }
}
