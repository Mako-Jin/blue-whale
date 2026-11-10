package com.bluewhale.erp.finance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付付款流水实体，对应表 {@code erp_fin_payable_settlement}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_fin_payable_settlement")
public class PayableSettlementEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 应付单 ID */
    private Long payableId;

    /** 付款日期 */
    private LocalDate settleDate;

    /** 付款金额 */
    private BigDecimal amount;

    /** 方式 */
    private String settleType;

    /** 流水号 */
    private String referenceNo;

    /** 备注 */
    private String remark;
}
