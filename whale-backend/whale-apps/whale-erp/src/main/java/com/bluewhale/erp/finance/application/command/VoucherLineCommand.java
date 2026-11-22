package com.bluewhale.erp.finance.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证分录命令。
 *
 * @author BlueWhale
 */
@Data
public class VoucherLineCommand {

    /** 科目编码 */
    @NotBlank(message = "{validation.erp.voucher-line.account-code-required}")
    private String accountCode;

    /** 摘要 */
    private String summary;

    /** 借方金额 */
    private BigDecimal debitAmount;

    /** 贷方金额 */
    private BigDecimal creditAmount;
}
