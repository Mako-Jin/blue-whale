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
    @NotNull(message = "{validation.erp.mes-completion-apply.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.erp.mes-completion-apply.quantity-min}")
    private BigDecimal quantity;
}
