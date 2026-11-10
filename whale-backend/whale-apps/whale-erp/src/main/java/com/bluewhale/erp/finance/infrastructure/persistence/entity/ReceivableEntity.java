package com.bluewhale.erp.finance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收台账实体，对应表 {@code erp_fin_receivable}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_fin_receivable")
public class ReceivableEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 应收单号 */
    private String docNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 币种 */
    private String currency;

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 已计提坏账 */
    private BigDecimal badDebtAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
