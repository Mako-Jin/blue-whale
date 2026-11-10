package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商机阶段历史实体，对应表 {@code crm_opportunity_stage_history}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_stage_history")
public class OpportunityStageHistoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 商机 ID */
    private Long opportunityId;

    /** 原阶段 */
    private String fromStage;

    /** 目标阶段 */
    private String toStage;

    /** 金额快照 */
    private BigDecimal amount;

    /** 概率快照 */
    private BigDecimal probability;

    /** 输单原因 */
    private String lostReason;

    /** 操作人 */
    private Long operatorId;

    /** 流转时间 */
    private LocalDateTime changedAt;
}
