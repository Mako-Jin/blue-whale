package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.service.FinanceReportAppService;
import com.bluewhale.erp.finance.application.view.AccountBalanceView;
import com.bluewhale.erp.finance.application.view.FinancialReportView;
import com.bluewhale.erp.finance.application.view.LedgerView;
import com.bluewhale.erp.finance.application.view.ReportRowView;
import com.bluewhale.erp.finance.application.view.VoucherLineView;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 财务报表应用服务实现。
 *
 * <p>余额口径：
 * <ul>
 *   <li>明细账：期初 = 起始期间之前的累计余额，期末 = 期初 + 本期发生额；</li>
 *   <li>科目余额表：发生额为指定期间，余额为累计至该期间；</li>
 *   <li>资产负债表：资产按借方净额，负债与权益按贷方净额，累计至该期间；</li>
 *   <li>利润表：仅指定期间的收入贷方净额与费用借方净额。</li>
 * </ul>
 * 收入与费用尚未结转的部分以「本年利润（未结转）」计入权益，保证报表可平衡。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FinanceReportAppServiceImpl implements FinanceReportAppService {

    /** 期间编码格式 */
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\d{6}");

    /** 期间解析格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 借方方向 */
    private static final String DIRECTION_DEBIT = "DEBIT";

    /** 资产科目 */
    private static final String TYPE_ASSET = "ASSET";

    /** 负债科目 */
    private static final String TYPE_LIABILITY = "LIABILITY";

    /** 权益科目 */
    private static final String TYPE_EQUITY = "EQUITY";

    /** 收入科目 */
    private static final String TYPE_REVENUE = "REVENUE";

    /** 费用科目 */
    private static final String TYPE_EXPENSE = "EXPENSE";

    /** 资产负债表类型 */
    private static final String REPORT_BALANCE_SHEET = "BALANCE_SHEET";

    /** 利润表类型 */
    private static final String REPORT_INCOME_STATEMENT = "INCOME_STATEMENT";

    /** 未结转利润虚拟科目编码 */
    private static final String UNCLOSED_PROFIT_CODE = "NET-PROFIT";

    /** 未结转利润行名称 */
    private static final String UNCLOSED_PROFIT_NAME = "本年利润（未结转）";

    private final VoucherRepository voucherRepository;

    private final AccountRepository accountRepository;

    private final AccountingPeriodRepository periodRepository;

    @Override
    public LedgerView ledger(String accountCode, String fromPeriod, String toPeriod) {
        Account account = accountRepository.findByCode(accountCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ACCOUNT_NOT_FOUND));
        String from = requirePeriod(fromPeriod);
        String to = requirePeriod(toPeriod);
        if (from.compareTo(to) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_STATE_CONFLICT, "起始期间不能晚于结束期间");
        }
        LedgerView view = new LedgerView();
        view.setAccountCode(account.getCode());
        view.setAccountName(account.getName());
        LineTotals opening = totalsOf(voucherRepository.listPostedLines(accountCode, null, previousPeriod(from)));
        view.setOpeningBalance(signedBalance(account, opening));
        List<VoucherLine> periodLines = voucherRepository.listPostedLines(accountCode, from, to);
        LineTotals movement = totalsOf(periodLines);
        view.setDebitTotal(movement.debit);
        view.setCreditTotal(movement.credit);
        view.setClosingBalance(view.getOpeningBalance().add(signedBalance(account, movement)));
        view.setLines(periodLines.stream().map(this::toLineView).toList());
        return view;
    }

    @Override
    public List<AccountBalanceView> accountBalances(String periodCode) {
        String period = requirePeriod(periodCode);
        Map<String, LineTotals> cumulative = group(voucherRepository.listPostedLines(null, null, period));
        Map<String, LineTotals> current = group(voucherRepository.listPostedLines(null, period, period));
        List<AccountBalanceView> views = new ArrayList<>();
        for (Account account : accountRepository.listEnabled()) {
            LineTotals cumulated = cumulative.get(account.getCode());
            LineTotals moved = current.get(account.getCode());
            BigDecimal balance = cumulated == null ? BigDecimal.ZERO : signedBalance(account, cumulated);
            BigDecimal debit = moved == null ? BigDecimal.ZERO : moved.debit;
            BigDecimal credit = moved == null ? BigDecimal.ZERO : moved.credit;
            if (balance.signum() == 0 && debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            AccountBalanceView view = new AccountBalanceView();
            view.setAccountCode(account.getCode());
            view.setAccountName(account.getName());
            view.setAccountType(account.getAccountType());
            view.setDirection(account.getDirection());
            view.setDebitTotal(debit);
            view.setCreditTotal(credit);
            view.setBalance(balance);
            views.add(view);
        }
        return views;
    }

    @Override
    public FinancialReportView balanceSheet(String periodCode) {
        String period = requirePeriod(periodCode);
        Map<String, LineTotals> totals = group(voucherRepository.listPostedLines(null, null, period));
        List<ReportRowView> rows = new ArrayList<>();
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Account account : accountRepository.listEnabled()) {
            LineTotals accountTotals = totals.get(account.getCode());
            if (accountTotals == null) {
                continue;
            }
            String type = account.getAccountType();
            if (TYPE_ASSET.equals(type)) {
                BigDecimal amount = accountTotals.netDebit();
                if (amount.signum() != 0) {
                    rows.add(row(account, amount));
                    assets = assets.add(amount);
                }
            } else if (TYPE_LIABILITY.equals(type)) {
                BigDecimal amount = accountTotals.netCredit();
                if (amount.signum() != 0) {
                    rows.add(row(account, amount));
                    liabilities = liabilities.add(amount);
                }
            } else if (TYPE_EQUITY.equals(type)) {
                BigDecimal amount = accountTotals.netCredit();
                if (amount.signum() != 0) {
                    rows.add(row(account, amount));
                    equity = equity.add(amount);
                }
            } else if (TYPE_REVENUE.equals(type)) {
                revenue = revenue.add(accountTotals.netCredit());
            } else if (TYPE_EXPENSE.equals(type)) {
                expense = expense.add(accountTotals.netDebit());
            }
        }
        BigDecimal netProfit = revenue.subtract(expense);
        if (netProfit.signum() != 0) {
            rows.add(virtualRow(UNCLOSED_PROFIT_CODE, UNCLOSED_PROFIT_NAME, TYPE_EQUITY, netProfit));
            equity = equity.add(netProfit);
        }
        FinancialReportView view = new FinancialReportView();
        view.setReportType(REPORT_BALANCE_SHEET);
        view.setPeriodCode(period);
        view.setRows(rows);
        view.setTotalAssets(assets);
        view.setTotalLiabilities(liabilities);
        view.setTotalEquity(equity);
        view.setNetProfit(netProfit);
        return view;
    }

    @Override
    public FinancialReportView incomeStatement(String periodCode) {
        String period = requirePeriod(periodCode);
        Map<String, LineTotals> totals = group(voucherRepository.listPostedLines(null, period, period));
        List<ReportRowView> rows = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Account account : accountRepository.listEnabled()) {
            LineTotals accountTotals = totals.get(account.getCode());
            if (accountTotals == null) {
                continue;
            }
            if (TYPE_REVENUE.equals(account.getAccountType())) {
                BigDecimal amount = accountTotals.netCredit();
                if (amount.signum() != 0) {
                    rows.add(row(account, amount));
                    revenue = revenue.add(amount);
                }
            } else if (TYPE_EXPENSE.equals(account.getAccountType())) {
                BigDecimal amount = accountTotals.netDebit();
                if (amount.signum() != 0) {
                    rows.add(row(account, amount));
                    expense = expense.add(amount);
                }
            }
        }
        FinancialReportView view = new FinancialReportView();
        view.setReportType(REPORT_INCOME_STATEMENT);
        view.setPeriodCode(period);
        view.setRows(rows);
        view.setTotalRevenue(revenue);
        view.setTotalExpenses(expense);
        view.setNetProfit(revenue.subtract(expense));
        return view;
    }

    /**
     * 校验期间编码格式且期间已维护。
     *
     * @param periodCode 期间编码
     * @return 规范化后的期间编码
     */
    private String requirePeriod(String periodCode) {
        String value = periodCode == null ? "" : periodCode.trim();
        if (!PERIOD_PATTERN.matcher(value).matches()) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_NOT_FOUND, "期间编码必须为 yyyyMM 格式");
        }
        AccountingPeriod period = periodRepository.findByCode(value)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PERIOD_NOT_FOUND));
        return period.getPeriodCode();
    }

    /**
     * 计算上一个期间。
     *
     * @param periodCode 期间编码
     * @return 上一个期间编码
     */
    private String previousPeriod(String periodCode) {
        return YearMonth.parse(periodCode, PERIOD_FORMATTER).minusMonths(1).format(PERIOD_FORMATTER);
    }

    /**
     * 按科目方向计算余额（贷方科目为贷借差）。
     *
     * @param account 科目
     * @param totals 借贷合计
     * @return 按科目方向为正的余额
     */
    private BigDecimal signedBalance(Account account, LineTotals totals) {
        return DIRECTION_DEBIT.equals(account.getDirection()) ? totals.netDebit() : totals.netCredit();
    }

    /**
     * 汇总一组分录的借贷合计。
     *
     * @param lines 分录列表
     * @return 借贷合计
     */
    private LineTotals totalsOf(List<VoucherLine> lines) {
        LineTotals totals = new LineTotals();
        for (VoucherLine line : lines) {
            totals.add(line.debit(), line.credit());
        }
        return totals;
    }

    /**
     * 按科目编码分组合计。
     *
     * @param lines 分录列表
     * @return 科目编码到借贷合计的有序映射
     */
    private Map<String, LineTotals> group(List<VoucherLine> lines) {
        Map<String, LineTotals> grouped = new TreeMap<>();
        for (VoucherLine line : lines) {
            grouped.computeIfAbsent(line.getAccountCode(), key -> new LineTotals())
                    .add(line.debit(), line.credit());
        }
        return grouped;
    }

    /**
     * 科目与余额转报表行。
     *
     * @param account 科目
     * @param amount 余额
     * @return 报表行
     */
    private ReportRowView row(Account account, BigDecimal amount) {
        return virtualRow(account.getCode(), account.getName(), account.getAccountType(), amount);
    }

    /**
     * 构造报表行。
     *
     * @param code 科目编码
     * @param name 科目名称
     * @param type 科目类型
     * @param amount 余额
     * @return 报表行
     */
    private ReportRowView virtualRow(String code, String name, String type, BigDecimal amount) {
        ReportRowView row = new ReportRowView();
        row.setAccountCode(code);
        row.setAccountName(name);
        row.setAccountType(type);
        row.setAmount(amount);
        return row;
    }

    /**
     * 分录转账簿行视图。
     *
     * @param line 分录
     * @return 账簿行
     */
    private VoucherLineView toLineView(VoucherLine line) {
        VoucherLineView view = new VoucherLineView();
        view.setVoucherNo(line.getVoucherNo());
        view.setVoucherDate(line.getVoucherDate());
        view.setLineNo(line.getLineNo());
        view.setAccountCode(line.getAccountCode());
        view.setAccountName(line.getAccountName());
        view.setSummary(line.getSummary());
        view.setDebitAmount(line.getDebitAmount());
        view.setCreditAmount(line.getCreditAmount());
        view.setCurrency(line.getCurrency());
        view.setExchangeRate(line.getExchangeRate());
        return view;
    }

    /**
     * 借贷合计（可变累加器）。
     */
    private static final class LineTotals {

        /** 借方合计 */
        private BigDecimal debit = BigDecimal.ZERO;

        /** 贷方合计 */
        private BigDecimal credit = BigDecimal.ZERO;

        /**
         * 累加一笔发生额。
         *
         * @param debitAmount 借方金额
         * @param creditAmount 贷方金额
         */
        private void add(BigDecimal debitAmount, BigDecimal creditAmount) {
            debit = debit.add(debitAmount);
            credit = credit.add(creditAmount);
        }

        /**
         * 借方净额。
         *
         * @return 借 − 贷
         */
        private BigDecimal netDebit() {
            return debit.subtract(credit);
        }

        /**
         * 贷方净额。
         *
         * @return 贷 − 借
         */
        private BigDecimal netCredit() {
            return credit.subtract(debit);
        }
    }
}
