package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 分渠道投递统计视图。
 *
 * @author BlueWhale
 */
@Data
public class ChannelStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道：INAPP / EMAIL / SMS / IM */
    private String channel;

    /** 该渠道投递总数 */
    private long total;

    /** 已提交渠道（在途） */
    private long sent;

    /** 渠道已确认送达 */
    private long delivered;

    /** 接收人已阅读 */
    private long read;

    /** 渠道退信 */
    private long bounced;

    /** 渠道调用失败 */
    private long failed;

    /** 主动跳过（未启用/缺少接收信息/限流熔断） */
    private long skipped;

    /** mock 模式 */
    private long mocked;

    /** 超过过期时间 */
    private long expired;

    /** 成功率（0~1，保留 4 位小数；无投递时为 0） */
    private double successRate;
}
