package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机聚合根。
 *
 * @author BlueWhale
 */
@Data
public class Opportunity {

    /** 主键 */
    private Long id;

    /** 所属客户 */
    private Long customerId;

    /** 商机名称 */
    private String name;

    /** 预计金额 */
    private BigDecimal amount;

    /** 阶段 */
    private OpportunityStage stage;

    /** 赢单概率 */
    private BigDecimal probability;

    /** 商机来源 */
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

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 判断是否已关闭。
     *
     * @return 赢单或输单返回 true
     */
    public boolean closed() {
        return stage != null && stage.terminal();
    }

    /**
     * 阶段流转。
     *
     * @param target 目标阶段
     * @param reason 输单原因（转 LOST 必填）
     * @param targetAmount 本次流转顺带更新金额（可为空）
     * @param at 流转时间
     */
    public void transitionTo(OpportunityStage target, String reason, BigDecimal targetAmount, LocalDateTime at) {
        if (stage == null || !stage.canMoveTo(target)) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT);
        }
        if (target == OpportunityStage.WON) {
            BigDecimal effective = targetAmount != null ? targetAmount : amount;
            if (effective == null || effective.compareTo(BigDecimal.ZERO) <= 0) {
                throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_AMOUNT_REQUIRED);
            }
            this.amount = effective;
        }
        if (target == OpportunityStage.LOST) {
            if (reason == null || reason.isBlank()) {
                throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_LOST_REASON_REQUIRED);
            }
            this.lostReason = reason.trim();
        }
        this.stage = target;
        this.probability = target.defaultProbability();
        this.stageEnteredAt = at;
        if (target.terminal()) {
            this.closedAt = at;
        }
    }
}
