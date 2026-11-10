package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务报表行。
 *
 * @author BlueWhale
 */
@Data
public class ReportRowView {

    /** 科目编码 */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 科目类型 */
    private String accountType;

    /** 余额 */
    private BigDecimal amount;
}
