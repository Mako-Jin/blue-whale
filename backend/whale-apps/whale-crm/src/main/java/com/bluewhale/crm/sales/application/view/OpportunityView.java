package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.Opportunity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机视图。
 *
 * @author BlueWhale
 */
@Data
public class OpportunityView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 商机名称 */
    private String name;

    /** 预计金额 */
    private BigDecimal amount;

    /** 阶段 */
    private String stage;

    /** 赢单概率 */
    private BigDecimal probability;

    /** 来源 */
    private String source;

    /** 竞争对手 */
    private String competitor;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 进入当前阶段时间 */
    private LocalDateTime stageEnteredAt;

    /** 预计关闭日期 */
    private LocalDate expectedCloseDate;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 输单原因 */
    private String lostReason;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域模型转视图。
     *
     * @param opportunity 商机
     * @return 视图
     */
    public static OpportunityView from(Opportunity opportunity) {
        OpportunityView view = new OpportunityView();
        view.setId(opportunity.getId());
        view.setCustomerId(opportunity.getCustomerId());
        view.setName(opportunity.getName());
        view.setAmount(opportunity.getAmount());
        view.setStage(opportunity.getStage() == null ? null : opportunity.getStage().name());
        view.setProbability(opportunity.getProbability());
        view.setSource(opportunity.getSource());
        view.setCompetitor(opportunity.getCompetitor());
        view.setNextActionAt(opportunity.getNextActionAt());
        view.setStageEnteredAt(opportunity.getStageEnteredAt());
        view.setExpectedCloseDate(opportunity.getExpectedCloseDate());
        view.setOwnerId(opportunity.getOwnerId());
        view.setDeptId(opportunity.getDeptId());
        view.setLostReason(opportunity.getLostReason());
        view.setClosedAt(opportunity.getClosedAt());
        view.setCreateTime(opportunity.getCreateTime());
        return view;
    }
}
