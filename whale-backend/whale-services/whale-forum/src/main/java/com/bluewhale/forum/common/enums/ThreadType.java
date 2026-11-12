package com.bluewhale.forum.common.enums;

/**
 * 帖子类型（对应 {@code fm_thread.thread_type}）。
 *
 * <p>类型决定详情页展示与可用操作，<b>不决定权限</b>：公告帖谁能发由权限码
 * {@code sys:forum:manage:dispose} 判定，不靠类型枚举兜底。</p>
 *
 * @author BlueWhale
 */
public enum ThreadType {

    /** 普通帖：标题 + 富文本正文 */
    NORMAL,

    /** 问答帖：可采纳最佳答案，采纳后进入 solved */
    QA,

    /** 投票帖：选项与截止时间独立存储，投票记录不占楼层号 */
    VOTE,

    /** 公告帖：只读发布，仅管理员/版主可发 */
    NOTICE,

    /** 活动帖：带报名入口，报名数据在 whale-party，本服务只存引用 */
    ACTIVITY,

    /** 长文帖：支持目录与分节（P2） */
    ARTICLE
}
