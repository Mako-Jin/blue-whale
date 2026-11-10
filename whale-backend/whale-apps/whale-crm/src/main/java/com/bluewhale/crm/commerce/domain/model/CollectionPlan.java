package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款计划分期。
 *
 * <p>分期合计必须等于合同金额；实收、税额与记账由 ERP 维护，CRM 只记录计划、
 * 承诺还款与催收动作。</p>
 *
 * @author BlueWhale
 */
@Data
public class CollectionPlan {

    /** 主键 */
    private Long id;

    /** CRM 合同台账 ID */
    private Long contractId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 分期序号（从 1 起） */
    private Integer installmentNo;

    /** 计划回款金额 */
    private BigDecimal plannedAmount;

    /** 计划回款日期 */
    private LocalDate plannedDate;

    /** 状态 */
    private CollectionPlanStatus status = CollectionPlanStatus.OPEN;

    /** 人工覆盖的催收优先级 */
    private String priorityOverride;

    /** 承诺还款日期 */
    private LocalDate promiseDate;

    /** 承诺还款金额 */
    private BigDecimal promiseAmount;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 逾期天数（未到期返回 0）。
     *
     * @return 逾期天数
     */
    public long overdueDays() {
        if (plannedDate == null) {
            return 0L;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(plannedDate, LocalDate.now());
        return Math.max(days, 0L);
    }

    /**
     * 是否逾期。
     *
     * @return 逾期返回 true
     */
    public boolean overdue() {
        return plannedDate != null && plannedDate.isBefore(LocalDate.now());
    }

    /**
     * 计算催收优先级。
     *
     * @return 优先级
     */
    public CollectionPriority priority() {
        return CollectionPriority.resolve(priorityOverride, plannedDate);
    }
}
