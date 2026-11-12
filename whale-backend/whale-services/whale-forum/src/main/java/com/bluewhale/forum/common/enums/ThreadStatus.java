package com.bluewhale.forum.common.enums;

/**
 * 帖子状态（对应 {@code fm_thread.status}）。
 *
 * <p>状态机与判定优先级见规格 v1.3 §七：
 * {@code deleted > closed > solved > pending > scheduled > published > draft}。
 * 同一帖多状态并存时按该优先级取展示值，避免前端各自发明顺序。</p>
 *
 * @author BlueWhale
 */
public enum ThreadStatus {

    /** 草稿：仅作者可见 */
    DRAFT,

    /** 定时发布：到点由 whale-job 抢占发布 */
    SCHEDULED,

    /** 待审：审核模式版块或命中敏感词转待审 */
    PENDING,

    /** 已发布 */
    PUBLISHED,

    /** 已解决：问答帖已采纳最佳答案，仍可回复 */
    SOLVED,

    /** 已关闭：禁止新回复，可重开 */
    CLOSED,

    /** 已驳回：审核未通过，作者可申诉 */
    REJECTED,

    /** 已删除：软删，保留审计 */
    DELETED
}
