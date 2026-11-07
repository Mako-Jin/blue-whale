package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量触达任务聚合。
 *
 * <p>状态机 {@code DRAFT → EXECUTING → COMPLETED}；存在失败明细时进入 {@code FAILED}，
 * 失败原因保留在 {@code lastError}，绝不伪造成功。</p>
 *
 * @author BlueWhale
 */
@Data
public class MarketingSend {

    /** 失败原因最大长度 */
    private static final int ERROR_MAX_LENGTH = 1000;

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 触达任务编号 */
    private String sendNo;

    /** 营销活动 ID */
    private Long campaignId;

    /** 人群快照 ID */
    private Long audienceId;

    /** 触达渠道 */
    private SendChannel channel;

    /** 消息模板编码 */
    private String templateCode;

    /** 消息主题 */
    private String subject;

    /** 内容文档 ID */
    private Long contentRefDocId;

    /** 任务状态 */
    private MarketingSendStatus status;

    /** 收件人总数 */
    private Integer totalCount;

    /** 成功数量 */
    private Integer sentCount;

    /** 跳过数量 */
    private Integer skippedCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 最近一次失败原因 */
    private String lastError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 进入执行态。
     *
     * @param at 开始执行时间
     */
    public void beginExecute(LocalDateTime at) {
        if (status == MarketingSendStatus.COMPLETED) {
            throw CrmErrorCode.exception(CrmErrorCode.MARKETING_SEND_INVALID, "触达任务已完成，不允许重复执行");
        }
        this.status = MarketingSendStatus.EXECUTING;
        this.executedAt = at;
    }

    /**
     * 结束执行并汇总计数；存在失败明细时任务状态为 {@code FAILED}。
     *
     * @param sent 成功数量
     * @param skipped 跳过数量
     * @param failed 失败数量
     * @param error 最近一次失败原因
     * @param at 结束时间
     */
    public void finish(int sent, int skipped, int failed, String error, LocalDateTime at) {
        this.sentCount = sent;
        this.skippedCount = skipped;
        this.failedCount = failed;
        this.executedAt = at;
        if (failed > 0) {
            this.status = MarketingSendStatus.FAILED;
            this.lastError = truncate(error);
        } else {
            this.status = MarketingSendStatus.COMPLETED;
            this.lastError = null;
        }
    }

    /**
     * 标记任务失败（如消息服务不可用）。
     *
     * @param error 失败原因
     * @param at 失败时间
     */
    public void markFailed(String error, LocalDateTime at) {
        this.status = MarketingSendStatus.FAILED;
        this.lastError = truncate(error);
        this.executedAt = at;
    }

    /**
     * 截断失败原因到列长度上限。
     *
     * @param error 失败原因
     * @return 截断后的文本
     */
    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= ERROR_MAX_LENGTH ? error : error.substring(0, ERROR_MAX_LENGTH);
    }
}
