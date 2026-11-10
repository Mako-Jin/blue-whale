package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 科目明细账视图。
 *
 * @author BlueWhale
 */
@Data
public class LedgerView {

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 期初余额（按科目方向为正） */
    private BigDecimal openingBalance;

    /** 期间借方发生额 */
    private BigDecimal debitTotal;

    /** 期间贷方发生额 */
    private BigDecimal creditTotal;

    /** 期末余额（按科目方向为正） */
    private BigDecimal closingBalance;

    /** 明细行 */
    private List<VoucherLineView> lines;
}
