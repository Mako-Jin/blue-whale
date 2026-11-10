package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证分录视图。
 *
 * @author BlueWhale
 */
@Data
public class VoucherLineView {

    /** 凭证号（账簿查询时回填） */
    private String voucherNo;

    /** 凭证日期（账簿查询时回填） */
    private LocalDate voucherDate;

    /** 行号 */
    private Integer lineNo;

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 摘要 */
    private String summary;

    /** 借方金额 */
    private BigDecimal debitAmount;

    /** 贷方金额 */
    private BigDecimal creditAmount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;
}
