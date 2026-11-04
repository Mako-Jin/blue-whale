package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 科目余额视图。
 *
 * @author BlueWhale
 */
@Data
public class AccountBalanceView {

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 科目类型 */
    private String accountType;

    /** 余额方向 */
    private String direction;

    /** 借方发生额 */
    private BigDecimal debitTotal;

    /** 贷方发生额 */
    private BigDecimal creditTotal;

    /** 余额（按科目方向为正） */
    private BigDecimal balance;
}
