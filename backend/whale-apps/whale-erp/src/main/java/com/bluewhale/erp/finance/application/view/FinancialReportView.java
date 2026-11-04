package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务报表视图。
 *
 * @author BlueWhale
 */
@Data
public class FinancialReportView {

    /** 报表类型：BALANCE_SHEET / INCOME_STATEMENT */
    private String reportType;

    /** 期间编码 */
    private String periodCode;

    /** 明细行 */
    private List<ReportRowView> rows;

    /** 资产合计 */
    private BigDecimal totalAssets;

    /** 负债合计 */
    private BigDecimal totalLiabilities;

    /** 权益合计 */
    private BigDecimal totalEquity;

    /** 收入合计 */
    private BigDecimal totalRevenue;

    /** 费用合计 */
    private BigDecimal totalExpenses;

    /** 净利润 */
    private BigDecimal netProfit;
}
