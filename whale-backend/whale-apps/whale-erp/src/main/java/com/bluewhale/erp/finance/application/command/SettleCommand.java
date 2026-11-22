package com.bluewhale.erp.finance.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收核销 / 应付付款 / 坏账计提命令。
 *
 * @author BlueWhale
 */
@Data
public class SettleCommand {

    /** 金额 */
    @NotNull(message = "{validation.erp.settle.amount-required}")
    private BigDecimal amount;

    /** 业务日期 */
    private LocalDate settleDate;

    /** 方式：CASH / BANK / OFFSET */
    private String settleType;

    /** 流水号 */
    private String referenceNo;

    /** 备注 */
    private String remark;
}
