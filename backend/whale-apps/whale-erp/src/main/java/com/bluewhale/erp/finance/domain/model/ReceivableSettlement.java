package com.bluewhale.erp.finance.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收核销流水。
 *
 * @author BlueWhale
 */
@Data
public class ReceivableSettlement {

    /** 主键 */
    private Long id;

    /** 应收单 ID */
    private Long receivableId;

    /** 核销日期 */
    private LocalDate settleDate;

    /** 核销金额 */
    private BigDecimal amount;

    /** 方式：CASH / BANK / OFFSET */
    private String settleType;

    /** 回款单号/流水号 */
    private String referenceNo;

    /** 备注 */
    private String remark;
}
