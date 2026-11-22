package com.bluewhale.erp.finance.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.finance.application.service.FinanceReportAppService;
import com.bluewhale.erp.finance.application.view.AccountBalanceView;
import com.bluewhale.erp.finance.application.view.FinancialReportView;
import com.bluewhale.erp.finance.application.view.LedgerView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 财务报表接口：明细账、科目余额表、资产负债表与利润表。
 *
 * <p>全部为只读查询，要求 {@code sys:erp:report:view} 权限；
 * 报表直接从已记账凭证取数，不做落库快照（M1）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/finance/report", version = "1")
@RequiredArgsConstructor
public class FinanceReportController {

    private final FinanceReportAppService financeReportAppService;

    /**
     * 科目明细账。
     *
     * @param accountCode 科目编码
     * @param fromPeriod 起始期间 yyyyMM
     * @param toPeriod 结束期间 yyyyMM
     * @return 明细账
     */
    @GetMapping("/ledger")
    @RequirePermission("sys:erp:report:view")
    public Result<LedgerView> ledger(@RequestParam String accountCode,
                                     @RequestParam String fromPeriod,
                                     @RequestParam String toPeriod) {
        return Result.ok(financeReportAppService.ledger(accountCode, fromPeriod, toPeriod));
    }

    /**
     * 科目余额表。
     *
     * @param period 期间编码 yyyyMM
     * @return 科目余额列表
     */
    @GetMapping("/account-balance")
    @RequirePermission("sys:erp:report:view")
    public Result<List<AccountBalanceView>> accountBalance(@RequestParam String period) {
        return Result.ok(financeReportAppService.accountBalances(period));
    }

    /**
     * 资产负债表。
     *
     * @param period 期间编码 yyyyMM
     * @return 资产负债表
     */
    @GetMapping("/balance-sheet")
    @RequirePermission("sys:erp:report:view")
    public Result<FinancialReportView> balanceSheet(@RequestParam String period) {
        return Result.ok(financeReportAppService.balanceSheet(period));
    }

    /**
     * 利润表。
     *
     * @param period 期间编码 yyyyMM
     * @return 利润表
     */
    @GetMapping("/income-statement")
    @RequirePermission("sys:erp:report:view")
    public Result<FinancialReportView> incomeStatement(@RequestParam String period) {
        return Result.ok(financeReportAppService.incomeStatement(period));
    }
}
