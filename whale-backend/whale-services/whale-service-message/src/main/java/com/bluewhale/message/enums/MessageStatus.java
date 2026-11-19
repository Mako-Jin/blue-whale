package com.bluewhale.message.enums;

/**
 * 逻辑消息状态（由全部投递结果聚合）。
 *
 * @author BlueWhale
 */
public enum MessageStatus {

    /** 已创建，尚未投递 */
    PENDING,

    /** 至少一条投递成功，且无失败 */
    SENT,

    /** 全部投递均已被渠道确认送达（回执聚合而来，无待送达/退信/失败） */
    DELIVERED,

    /** 部分投递失败 */
    PARTIAL,

    /** 全部投递失败 */
    FAILED,

    /** mock 模式：没有真实投递 */
    MOCKED,

    /** 全部渠道均被跳过 */
    SKIPPED,

    /** 全部投递均因消息过期未投递 */
    EXPIRED
}
