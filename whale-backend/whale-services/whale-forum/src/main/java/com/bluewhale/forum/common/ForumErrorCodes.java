package com.bluewhale.forum.common;

/**
 * 论坛服务错误码（30xxx 段，口径见需求规格 v1.3 §九）。
 *
 * <p>错误码即验收判据：前端按码分支（如 30020 弹「驳回必须填写意见」），
 * 改码等于改契约，必须同步规格与前端。</p>
 *
 * @author BlueWhale
 */
public final class ForumErrorCodes {

    /** 帖子/楼层不存在或不可见（统一返回该码，不泄露存在性） */
    public static final int THREAD_NOT_FOUND = 30001;

    /** 标题超长 */
    public static final int TITLE_TOO_LONG = 30002;

    /** 正文超长 */
    public static final int CONTENT_TOO_LONG = 30003;

    /** 楼层号冲突（重试耗尽） */
    public static final int FLOOR_CONFLICT = 30010;

    /** 楼层已达上限，帖子已关闭 */
    public static final int FLOOR_LIMIT_REACHED = 30011;

    /** 驳回/退回未填写意见 */
    public static final int AUDIT_REMARK_REQUIRED = 30020;

    /** 内容消毒失败（含危险标签/属性） */
    public static final int CONTENT_REJECTED = 30030;

    /** 敏感词拦截 */
    public static final int SENSITIVE_BLOCKED = 30031;

    /** 版块不存在 / 不可见 / 只读，禁止发帖 */
    public static final int BOARD_NOT_POSTABLE = 30040;

    /** 禁言中，禁止发帖或回复 */
    public static final int USER_MUTED = 30041;

    /** 操作过于频繁（发帖/回帖间隔限制） */
    public static final int RATE_LIMITED = 30042;

    /** 匿名场景不支持该操作 */
    public static final int ANONYMOUS_UNSUPPORTED = 30050;

    /** 没有该帖的编辑/删除权限（非作者且无治理权限） */
    public static final int NOT_THREAD_OWNER = 30060;

    /** 编辑超出时间窗口（作者仅 30 分钟内可改） */
    public static final int EDIT_WINDOW_EXPIRED = 30061;

    /** 帖子状态不允许当前操作（如未发布就回帖） */
    public static final int THREAD_STATE_ILLEGAL = 30062;

    /** 版块编码重复 */
    public static final int BOARD_CODE_DUPLICATED = 30070;

    /**
     * 私有构造器：常量类，禁止实例化
     */
    private ForumErrorCodes() {
    }
}
