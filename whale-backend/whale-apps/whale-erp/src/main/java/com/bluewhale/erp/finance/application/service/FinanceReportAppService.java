package com.bluewhale.erp.finance.application.service;

import com.bluewhale.erp.finance.application.view.AccountBalanceView;
import com.bluewhale.erp.finance.application.view.FinancialReportView;
import com.bluewhale.erp.finance.application.view.LedgerView;

import java.util.List;

/**
 * 财务报表应用服务：明细账、科目余额表、资产负债表与利润表。
 *
 * <p>M1 报表直接基于已记账凭证分录取数，不引入独立的报表快照表；
 * 已红冲凭证与其红冲凭证同样计入，净额为零，保证可追溯。</p>
 *
 * @author BlueWhale
 */
public interface FinanceReportAppService {

    /**
     * 查询科目明细账。
     *
     * @param accountCode 科目编码
     * @param fromPeriod 起始期间 yyyyMM
     * @param toPeriod 结束期间 yyyyMM
     * @return 明细账（含期初、本期发生额、期末与明细行）
     */
    LedgerView ledger(String accountCode, String fromPeriod, String toPeriod);

    /**
     * 查询科目余额表。
     *
     * @param periodCode 期间编码 yyyyMM
     * @return 科目余额列表（按科目编码升序）
     */
    List<AccountBalanceView> accountBalances(String periodCode);

    /**
     * 查询资产负债表（累计至指定期间）。
     *
     * @param periodCode 期间编码 yyyyMM
     * @return 资产负债表
     */
    FinancialReportView balanceSheet(String periodCode);

    /**
     * 查询利润表（仅指定期间发生额）。
     *
     * @param periodCode 期间编码 yyyyMM
     * @return 利润表
     */
    FinancialReportView incomeStatement(String periodCode);
}
