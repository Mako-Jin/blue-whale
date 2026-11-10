package com.bluewhale.crm.sales.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商机阶段流转历史（append-only）。
 *
 * @author BlueWhale
 */
@Data
public class OpportunityStageHistory {

    /** 主键 */
    private Long id;

    /** 商机 ID */
    private Long opportunityId;

    /** 原阶段；创建时为空 */
    private OpportunityStage fromStage;

    /** 目标阶段 */
    private OpportunityStage toStage;

    /** 流转时金额快照 */
    private BigDecimal amount;

    /** 流转时概率快照 */
    private BigDecimal probability;

    /** 输单原因 */
    private String lostReason;

    /** 操作人 */
    private Long operatorId;

    /** 流转时间 */
    private LocalDateTime changedAt;
}
