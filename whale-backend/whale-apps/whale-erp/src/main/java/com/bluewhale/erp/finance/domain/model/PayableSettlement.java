package com.bluewhale.erp.finance.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付付款流水。
 *
 * @author BlueWhale
 */
@Data
public class PayableSettlement {

    /** 主键 */
    private Long id;

    /** 应付单 ID */
    private Long payableId;

    /** 付款日期 */
    private LocalDate settleDate;

    /** 付款金额 */
    private BigDecimal amount;

    /** 方式：CASH / BANK / OFFSET */
    private String settleType;

    /** 付款单号/流水号 */
    private String referenceNo;

    /** 备注 */
    private String remark;
}
