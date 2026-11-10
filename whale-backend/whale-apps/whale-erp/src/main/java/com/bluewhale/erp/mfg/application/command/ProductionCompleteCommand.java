package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产完工入库命令。
 *
 * @author BlueWhale
 */
@Data
public class ProductionCompleteCommand {

    /** 完工日期 */
    @NotNull
    private LocalDate completeDate;

    /** 本次完工数量 */
    @NotNull
    private BigDecimal quantity;
}
