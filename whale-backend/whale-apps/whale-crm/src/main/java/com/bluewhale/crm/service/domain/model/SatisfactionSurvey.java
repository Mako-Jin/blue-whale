package com.bluewhale.crm.service.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满意度问卷（一次性令牌只存哈希）。
 *
 * @author BlueWhale
 */
@Data
public class SatisfactionSurvey {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 问卷编号（CSAT-yyyyMMdd-xxxx） */
    private String surveyNo;

    /** 工单 ID */
    private Long ticketId;

    /** 一次性令牌哈希（HMAC-SHA256） */
    private String tokenHash;

    /** 问卷状态 */
    private SurveyStatus status;

    /** 生成 / 发送时间 */
    private LocalDateTime sentAt;

    /** 令牌有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 答卷提交时间 */
    private LocalDateTime usedAt;

    /**
     * 签发问卷。
     *
     * @param expireDays 有效天数
     * @param now 签发时间
     */
    public void issue(int expireDays, LocalDateTime now) {
        this.status = SurveyStatus.SENT;
        this.sentAt = now;
        this.expiresAt = now.plusDays(Math.max(1, expireDays));
        this.usedAt = null;
    }

    /**
     * 作废问卷。
     *
     * @param now 作废时间
     */
    public void revoke(LocalDateTime now) {
        this.status = SurveyStatus.REVOKED;
        this.usedAt = now;
    }

    /**
     * 标记已答卷。
     *
     * @param now 提交时间
     */
    public void markUsed(LocalDateTime now) {
        this.status = SurveyStatus.USED;
        this.usedAt = now;
    }

    /**
     * 判断令牌是否已过期。
     *
     * @param now 当前时间
     * @return 是否过期
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
