package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.CollectionAction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收记录视图。
 *
 * @author BlueWhale
 */
@Data
public class CollectionActionView {

    /** 主键 */
    private Long id;

    /** 回款计划 ID */
    private Long planId;

    /** 合同台账 ID */
    private Long contractId;

    /** 催收动作 */
    private String actionType;

    /** 催收内容 */
    private String content;

    /** 承诺还款日期 */
    private LocalDate promiseDate;

    /** 承诺还款金额 */
    private BigDecimal promiseAmount;

    /** 操作人 */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域模型转视图。
     *
     * @param action 催收记录
     * @return 视图
     */
    public static CollectionActionView from(CollectionAction action) {
        CollectionActionView view = new CollectionActionView();
        view.setId(action.getId());
        view.setPlanId(action.getPlanId());
        view.setContractId(action.getContractId());
        view.setActionType(action.getActionType());
        view.setContent(action.getContent());
        view.setPromiseDate(action.getPromiseDate());
        view.setPromiseAmount(action.getPromiseAmount());
        view.setOperatorId(action.getOperatorId());
        view.setCreateTime(action.getCreateTime());
        return view;
    }
}
