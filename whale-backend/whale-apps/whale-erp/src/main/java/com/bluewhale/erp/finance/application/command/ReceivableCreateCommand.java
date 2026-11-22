package com.bluewhale.erp.finance.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收单创建命令。
 *
 * @author BlueWhale
 */
@Data
public class ReceivableCreateCommand {

    /** 客户 ID */
    @NotNull(message = "{validation.erp.receivable.customer-id-required}")
    private Long customerId;

    /** 单号（为空时自动生成） */
    private String docNo;

    /** 单据日期 */
    @NotNull(message = "{validation.erp.receivable.doc-date-required}")
    private LocalDate docDate;

    /** 到期日 */
    @NotNull(message = "{validation.erp.receivable.due-date-required}")
    private LocalDate dueDate;

    /** 应收金额 */
    @NotNull(message = "{validation.erp.receivable.amount-required}")
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
