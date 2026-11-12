package com.bluewhale.forum.common.enums;

/**
 * 处置与审核动作（对应 {@code fm_moderation_record.action}）。
 *
 * <p>全部动作 append-only 留痕，不设 {@code update_time/deleted}：
 * 处置记录一旦可改，申诉与合规追溯就失去依据。</p>
 *
 * @author BlueWhale
 */
public enum ModerationAction {

    /** 审核通过 */
    AUDIT_PASS,

    /** 审核驳回（意见必填，30020） */
    AUDIT_REJECT,

    /** 悬榜（置顶） */
    PIN,

    /** 取消悬榜 */
    UNPIN,

    /** 题金榜（加精） */
    ESSENCE,

    /** 撤销精华 */
    UNESSENCE,

    /** 关闭帖子（禁新回复） */
    LOCK,

    /** 重开帖子 */
    UNLOCK,

    /** 违规删除 */
    REMOVE,

    /** 禁言（1/7/30 天） */
    MUTE,

    /** 解除禁言 */
    UNMUTE,

    /** 封号 */
    BAN
}
