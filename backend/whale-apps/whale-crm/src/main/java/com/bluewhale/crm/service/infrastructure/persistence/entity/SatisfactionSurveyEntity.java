package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 满意度问卷实体，对应表 {@code crm_satisfaction_survey}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_satisfaction_survey")
public class SatisfactionSurveyEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 问卷编号 */
    private String surveyNo;

    /** 工单 ID */
    private Long ticketId;

    /** 令牌哈希 */
    private String tokenHash;

    /** 问卷状态 */
    private String status;

    /** 发送时间 */
    private LocalDateTime sentAt;

    /** 有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 答卷提交时间 */
    private LocalDateTime usedAt;
}
