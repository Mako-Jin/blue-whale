package com.bluewhale.erp.finance.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证分录。
 *
 * <p>同一分录只允许借记或贷记一侧有金额，两侧同时有金额视为非法。
 * {@code voucherNo} / {@code voucherDate} 为账簿查询时的展示字段，落库时不写。</p>
 *
 * @author BlueWhale
 */
@Data
public class VoucherLine {

    /** 凭证 ID（明细查询时回填） */
    private Long voucherId;

    /** 凭证号（明细查询时回填） */
    private String voucherNo;

    /** 凭证日期（明细查询时回填） */
    private LocalDate voucherDate;

    /** 行号 */
    private Integer lineNo;

    /** 科目编码 */
    private String accountCode;

    /** 科目名称（快照） */
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

    /**
     * 借方金额，空值按零处理。
     *
     * @return 借方金额
     */
    public BigDecimal debit() {
        return debitAmount == null ? BigDecimal.ZERO : debitAmount;
    }

    /**
     * 贷方金额，空值按零处理。
     *
     * @return 贷方金额
     */
    public BigDecimal credit() {
        return creditAmount == null ? BigDecimal.ZERO : creditAmount;
    }
}
