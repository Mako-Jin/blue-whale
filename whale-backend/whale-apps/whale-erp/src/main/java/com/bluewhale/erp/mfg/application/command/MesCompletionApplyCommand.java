package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工确认 MES 完工入库命令。
 *
 * @author BlueWhale
 */
@Data
public class MesCompletionApplyCommand {

    /** 本次入库数量 */
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.0001", message = "入库数量必须大于 0")
    private BigDecimal quantity;
}
