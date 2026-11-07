package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 承诺还款命令。
 *
 * @author BlueWhale
 */
@Data
public class CollectionPromiseCommand {

    /** 承诺还款日期 */
    @NotNull
    private LocalDate promiseDate;

    /** 承诺还款金额 */
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal promiseAmount;

    /** 备注 */
    private String remark;
}
