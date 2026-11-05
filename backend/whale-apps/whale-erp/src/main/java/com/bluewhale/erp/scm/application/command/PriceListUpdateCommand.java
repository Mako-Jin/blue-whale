package com.bluewhale.erp.scm.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新价目命令：单价、有效期、备注与状态。
 *
 * @author BlueWhale
 */
@Data
public class PriceListUpdateCommand {

    /** 单价 */
    @NotNull
    @Positive
    private BigDecimal price;

    /** 有效期起（空表示不限） */
    private LocalDate validFrom;

    /** 有效期止（空表示不限） */
    private LocalDate validTo;

    /** 备注 */
    private String remark;

    /** 状态：为空表示保持原状态 */
    private String status;
}
