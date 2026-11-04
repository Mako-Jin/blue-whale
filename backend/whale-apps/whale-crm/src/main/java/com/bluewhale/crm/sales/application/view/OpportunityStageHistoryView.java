package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.OpportunityStageHistory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商机阶段历史视图。
 *
 * @author BlueWhale
 */
@Data
public class OpportunityStageHistoryView {

    /** 主键 */
    private Long id;

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

    /**
     * 领域模型转视图。
     *
     * @param history 阶段历史
     * @return 视图
     */
    public static OpportunityStageHistoryView from(OpportunityStageHistory history) {
        OpportunityStageHistoryView view = new OpportunityStageHistoryView();
        view.setId(history.getId());
        view.setOpportunityId(history.getOpportunityId());
        view.setFromStage(history.getFromStage() == null ? null : history.getFromStage().name());
        view.setToStage(history.getToStage() == null ? null : history.getToStage().name());
        view.setAmount(history.getAmount());
        view.setProbability(history.getProbability());
        view.setLostReason(history.getLostReason());
        view.setOperatorId(history.getOperatorId());
        view.setChangedAt(history.getChangedAt());
        return view;
    }
}
