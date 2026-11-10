package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 不合格品处置命令。
 *
 * @author BlueWhale
 */
@Data
public class NcrDisposeCommand {

    /** 处置方式：REWORK / CONCESSION / SCRAP / RETURN */
    @NotBlank
    private String disposition;

    /** 处置日期 */
    @NotNull
    private LocalDate disposeDate;

    /** 质量成本金额 */
    private BigDecimal costAmount;

    /** 备注 */
    private String remark;
}
