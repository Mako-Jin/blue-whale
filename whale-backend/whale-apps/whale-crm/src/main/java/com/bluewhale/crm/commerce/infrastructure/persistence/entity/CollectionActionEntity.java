package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 催收记录实体，对应表 {@code crm_collection_action}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_collection_action")
public class CollectionActionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 回款计划分期 ID */
    private Long planId;

    /** CRM 合同台账 ID */
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
}
