package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量触达明细。
 *
 * @author BlueWhale
 */
@Data
public class MarketingSendItem {

    /** 失败原因最大长度 */
    private static final int ERROR_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 触达任务 ID */
    private Long sendId;

    /** 营销活动 ID（幂等键组成部分） */
    private Long campaignId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名快照 */
    private String contactName;

    /** 收件人 */
    private String recipient;

    /** 触达渠道 */
    private SendChannel channel;

    /** 明细状态 */
    private SendItemStatus status;

    /** 失败原因 */
    private String error;

    /** 发送时间 */
    private LocalDateTime sentAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 标记发送成功。
     *
     * @param at 发送时间
     */
    public void markSent(LocalDateTime at) {
        this.status = SendItemStatus.SENT;
        this.sentAt = at;
        this.error = null;
    }

    /**
     * 标记跳过。
     *
     * @param reason 跳过原因
     */
    public void markSkipped(String reason) {
        this.status = SendItemStatus.SKIPPED;
        this.error = truncate(reason);
    }

    /**
     * 标记发送失败。
     *
     * @param reason 失败原因
     */
    public void markFailed(String reason) {
        this.status = SendItemStatus.FAILED;
        this.error = truncate(reason);
    }

    /**
     * 是否待发送。
     *
     * @return 待发送返回 true
     */
    public boolean isPending() {
        return status == null || status == SendItemStatus.PENDING;
    }

    /**
     * 截断失败原因到列长度上限。
     *
     * @param reason 原因
     * @return 截断后的文本
     */
    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= ERROR_MAX_LENGTH ? reason : reason.substring(0, ERROR_MAX_LENGTH);
    }
}
