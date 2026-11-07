package com.bluewhale.crm.service.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满意度答卷（一问卷一答卷）。
 *
 * @author BlueWhale
 */
@Data
public class SatisfactionResponse {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 问卷 ID */
    private Long surveyId;

    /** 工单 ID */
    private Long ticketId;

    /** 总分（1–5） */
    private Integer score;

    /** 客户评论 */
    private String comment;

    /** 响应速度评分（1–5） */
    private Integer speedScore;

    /** 服务态度评分（1–5） */
    private Integer attitudeScore;

    /** 专业程度评分（1–5） */
    private Integer professionalScore;

    /** 是否低分 */
    private Boolean lowScore;

    /** 是否需回访 */
    private Boolean followUpRequired;

    /** 提交时间 */
    private LocalDateTime submittedAt;
}
