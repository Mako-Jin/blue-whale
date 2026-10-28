package com.bluewhale.message.enums;

/**
 * 单条投递结果状态。
 *
 * @author BlueWhale
 */
public enum DeliveryStatus {

    /** 已提交给渠道 */
    SENT,

    /** 渠道调用失败 */
    FAILED,

    /** 渠道未启用/缺少接收信息，主动跳过 */
    SKIPPED,

    /** mock 模式：未发起真实请求 */
    MOCKED
}
