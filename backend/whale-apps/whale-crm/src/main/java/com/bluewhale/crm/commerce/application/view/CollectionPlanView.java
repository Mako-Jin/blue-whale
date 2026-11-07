package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.CollectionPlan;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款计划视图。
 *
 * @author BlueWhale
 */
@Data
public class CollectionPlanView {

    /** 主键 */
    private Long id;

    /** 合同台账 ID */
    private Long contractId;

    /** 客户 ID */
    private Long customerId;

    /** 分期序号 */
    private Integer installmentNo;

    /** 计划回款金额 */
    private BigDecimal plannedAmount;

    /** 计划回款日期 */
    private LocalDate plannedDate;

    /** 状态 */
    private String status;

    /** 催收优先级 */
    private String priority;

    /** 承诺还款日期 */
    private LocalDate promiseDate;

    /** 承诺还款金额 */
    private BigDecimal promiseAmount;

    /** 逾期天数 */
    private Long overdueDays;

    /** 是否逾期 */
    private Boolean overdue;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 领域模型转视图。
     *
     * @param plan 回款计划
     * @return 视图
     */
    public static CollectionPlanView from(CollectionPlan plan) {
        CollectionPlanView view = new CollectionPlanView();
        view.setId(plan.getId());
        view.setContractId(plan.getContractId());
        view.setCustomerId(plan.getCustomerId());
        view.setInstallmentNo(plan.getInstallmentNo());
        view.setPlannedAmount(plan.getPlannedAmount());
        view.setPlannedDate(plan.getPlannedDate());
        view.setStatus(plan.getStatus() == null ? null : plan.getStatus().name());
        view.setPriority(plan.priority().name());
        view.setPromiseDate(plan.getPromiseDate());
        view.setPromiseAmount(plan.getPromiseAmount());
        view.setOverdueDays(plan.overdueDays());
        view.setOverdue(plan.overdue());
        view.setOwnerId(plan.getOwnerId());
        view.setDeptId(plan.getDeptId());
        view.setRemark(plan.getRemark());
        view.setCreateTime(plan.getCreateTime());
        view.setUpdateTime(plan.getUpdateTime());
        return view;
    }
}
