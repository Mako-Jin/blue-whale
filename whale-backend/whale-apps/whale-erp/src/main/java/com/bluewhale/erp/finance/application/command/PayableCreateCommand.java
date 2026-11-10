package com.bluewhale.erp.finance.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付单创建命令。
 *
 * @author BlueWhale
 */
@Data
public class PayableCreateCommand {

    /** 供应商 ID */
    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 单号（为空时自动生成） */
    private String docNo;

    /** 单据日期 */
    @NotNull(message = "单据日期不能为空")
    private LocalDate docDate;

    /** 到期日 */
    @NotNull(message = "到期日不能为空")
    private LocalDate dueDate;

    /** 应付金额 */
    @NotNull(message = "应付金额不能为空")
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 备注 */
    private String remark;
}
