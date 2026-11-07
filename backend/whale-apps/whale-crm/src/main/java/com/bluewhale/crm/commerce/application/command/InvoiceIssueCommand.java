package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 登记开票记录命令。
 *
 * @author BlueWhale
 */
@Data
public class InvoiceIssueCommand {

    /** 发票代码 */
    private String invoiceCode;

    /** 发票号码 */
    private String invoiceNumber;

    /** 实际开票金额 */
    @NotNull
    private BigDecimal issuedAmount;

    /** 税额 */
    private BigDecimal taxAmount;
}
