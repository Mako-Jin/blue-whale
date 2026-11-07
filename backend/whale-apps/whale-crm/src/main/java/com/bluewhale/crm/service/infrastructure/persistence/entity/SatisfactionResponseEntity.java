package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 满意度答卷实体，对应表 {@code crm_satisfaction_response}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_satisfaction_response")
public class SatisfactionResponseEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 问卷 ID */
    private Long surveyId;

    /** 工单 ID */
    private Long ticketId;

    /** 总分 */
    private Integer score;

    /** 客户评论 */
    private String comment;

    /** 响应速度评分 */
    private Integer speedScore;

    /** 服务态度评分 */
    private Integer attitudeScore;

    /** 专业程度评分 */
    private Integer professionalScore;

    /** 是否低分 */
    private Boolean lowScore;

    /** 是否需回访 */
    private Boolean followUpRequired;

    /** 提交时间 */
    private LocalDateTime submittedAt;
}
