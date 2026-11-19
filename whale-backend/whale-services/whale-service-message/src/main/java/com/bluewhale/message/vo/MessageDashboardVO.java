package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递看板视图：单次聚合返回总览、分渠道成功率、失败 TopN 与未匹配回执数。
 *
 * @author BlueWhale
 */
@Data
public class MessageDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 窗口开始时间 */
    private LocalDateTime startTime;

    /** 窗口结束时间 */
    private LocalDateTime endTime;

    /** 消息总数（按创建时间） */
    private long totalMessages;

    /** 投递总数 */
    private long totalDeliveries;

    /** 成功投递数：DELIVERED + READ */
    private long successCount;

    /** 失败投递数：FAILED + BOUNCED */
    private long failedCount;

    /** 投递成功率（0~1，保留 4 位小数；无投递时为 0） */
    private double successRate;

    /** 窗口内未匹配到投递记录的回执数 */
    private long unmatchedReceipts;

    /** 分渠道统计 */
    private List<ChannelStatVO> channels;

    /** 失败原因 TopN（按条数倒序） */
    private List<FailureStatVO> topFailures;
}
