package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 催收记录（追加式）。
 *
 * @author BlueWhale
 */
@Data
public class CollectionAction {

    /** 主键 */
    private Long id;

    /** 回款计划分期 ID */
    private Long planId;

    /** CRM 合同台账 ID */
    private Long contractId;

    /** 催收动作：CALL / VISIT / EMAIL / REMINDER / PROMISE */
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
}
