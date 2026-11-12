package com.bluewhale.forum.common.enums;

/**
 * 举报工单状态（对应 {@code fm_report.status}）。
 *
 * <p>与帖子状态是两条链路：举报受理<b>不自动改帖状态</b>，处置是独立动作；
 * {@code REJECTED}（举报不成立）<b>绝不通知被举报人</b>——否则等于告诉对方
 * 「有人在举报你」，既泄露举报人存在也激化矛盾。</p>
 *
 * @author BlueWhale
 */
public enum ReportStatus {

    /** 待受理（SLA 24h） */
    PENDING,

    /** 处理中（SLA 48h，超时由 whale-job 升级） */
    HANDLING,

    /** 已处置：被举报人收到处置通知 */
    RESOLVED,

    /** 举报不成立：只通知举报人 */
    REJECTED
}
