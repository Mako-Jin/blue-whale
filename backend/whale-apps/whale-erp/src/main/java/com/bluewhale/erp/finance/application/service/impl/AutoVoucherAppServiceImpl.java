package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.ErpFinanceAccountProperties;
import com.bluewhale.erp.finance.application.command.AutoVoucherCommand;
import com.bluewhale.erp.finance.application.service.AutoVoucherAppService;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动记账应用服务实现。
 *
 * <p>幂等：以 {@code sourceType + sourceId} 查询已生成凭证，重复触发直接返回原凭证 ID。
 * 科目映射来自 {@link ErpFinanceAccountProperties}，D5 确认后只改配置。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AutoVoucherAppServiceImpl implements AutoVoucherAppService {

    /** 期间编码格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final VoucherRepository voucherRepository;

    private final AccountRepository accountRepository;

    private final AccountingPeriodRepository periodRepository;

    private final ErpFinanceAccountProperties accountProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postReceipt(AutoVoucherCommand command) {
        return post(command, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postShipment(AutoVoucherCommand command) {
        return post(command, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postStockVariance(AutoVoucherCommand command) {
        if (Amounts.isZero(command.getAmount())) {
            return null;
        }
        Long existing = existingId(command);
        if (existing != null) {
            return existing;
        }
        boolean gain = command.getAmount().signum() > 0;
        BigDecimal amount = command.getAmount().abs();
        Amounts.requirePositive(amount, "库存差异金额");
        AccountingPeriod period = requireOpenPeriod(command.getVoucherDate());
        String currency = StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY;
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(command.getVoucherDate());
        voucher.setPeriodCode(period.getPeriodCode());
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);
        voucher.setCurrency(currency);
        voucher.setExchangeRate(BigDecimal.ONE);
        voucher.setSourceType(command.getSourceType());
        voucher.setSourceId(command.getSourceId());
        voucher.setRemark(command.getRemark());
        List<VoucherLine> lines = new ArrayList<>();
        if (gain) {
            addLine(voucher, lines, accountProperties.getInventory(), amount, null, "库存盘盈");
            addLine(voucher, lines, accountProperties.getStockVariance(), null, amount, "库存盘盈");
        } else {
            addLine(voucher, lines, accountProperties.getStockVariance(), amount, null, "库存盘亏");
            addLine(voucher, lines, accountProperties.getInventory(), null, amount, "库存盘亏");
        }
        voucher.setLines(lines);
        voucher.validateAndSum();
        voucher.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(voucher);
        voucher.post(command.getOperatorId(), LocalDateTime.now());
        voucherRepository.update(voucher);
        return voucher.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postProductionIssue(AutoVoucherCommand command) {
        return postProduction(command, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postProductionReceipt(AutoVoucherCommand command) {
        return postProduction(command, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postPayrollAccrual(AutoVoucherCommand command) {
        return postPayroll(command, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long postPayrollPayment(AutoVoucherCommand command) {
        return postPayroll(command, true);
    }

    /**
     * 生成并记账薪资凭证。
     *
     * @param command 记账命令
     * @param payment 是否发放（否则为计提）
     * @return 凭证 ID；金额为零时返回 null
     */
    private Long postPayroll(AutoVoucherCommand command, boolean payment) {
        if (Amounts.isZero(command.getAmount())) {
            return null;
        }
        Long existing = existingId(command);
        if (existing != null) {
            return existing;
        }
        Amounts.requirePositive(command.getAmount(), "薪资记账金额");
        AccountingPeriod period = requireOpenPeriod(command.getVoucherDate());
        String currency = StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY;
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(command.getVoucherDate());
        voucher.setPeriodCode(period.getPeriodCode());
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);
        voucher.setCurrency(currency);
        voucher.setExchangeRate(BigDecimal.ONE);
        voucher.setSourceType(command.getSourceType());
        voucher.setSourceId(command.getSourceId());
        voucher.setRemark(command.getRemark());
        List<VoucherLine> lines = new ArrayList<>();
        if (payment) {
            addLine(voucher, lines, accountProperties.getSalaryPayable(), command.getAmount(), null, "薪资发放");
            addLine(voucher, lines, accountProperties.getBankDeposit(), null, command.getNetAmount(), "薪资发放");
            addOptionalLine(voucher, lines, accountProperties.getTaxPayable(), command.getTaxAmount(), "代扣个税");
            addOptionalLine(voucher, lines, accountProperties.getOtherPayable(), command.getWithholdingAmount(),
                    "代扣社保公积金");
        } else {
            addLine(voucher, lines, accountProperties.getSalaryExpense(), command.getAmount(), null, "计提工资");
            addLine(voucher, lines, accountProperties.getSalaryPayable(), null, command.getAmount(), "计提工资");
        }
        voucher.setLines(lines);
        voucher.validateAndSum();
        voucher.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(voucher);
        voucher.post(command.getOperatorId(), LocalDateTime.now());
        voucherRepository.update(voucher);
        return voucher.getId();
    }

    /**
     * 追加一条贷方分录；金额为空或为零时跳过。
     *
     * @param voucher 凭证
     * @param lines 分录集合
     * @param accountCode 科目编码
     * @param amount 贷方金额
     * @param summary 摘要
     */
    private void addOptionalLine(Voucher voucher, List<VoucherLine> lines, String accountCode,
                                 BigDecimal amount, String summary) {
        if (amount != null && amount.signum() > 0) {
            addLine(voucher, lines, accountCode, null, amount, summary);
        }
    }

    /**
     * 生成并记账生产凭证。
     *
     * @param command 记账命令
     * @param receipt 是否完工入库（否则为生产领料）
     * @return 凭证 ID；金额为零时返回 null
     */
    private Long postProduction(AutoVoucherCommand command, boolean receipt) {
        if (Amounts.isZero(command.getAmount())) {
            return null;
        }
        Long existing = existingId(command);
        if (existing != null) {
            return existing;
        }
        Amounts.requirePositive(command.getAmount(), "生产记账金额");
        AccountingPeriod period = requireOpenPeriod(command.getVoucherDate());
        String currency = StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY;
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(command.getVoucherDate());
        voucher.setPeriodCode(period.getPeriodCode());
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);
        voucher.setCurrency(currency);
        voucher.setExchangeRate(BigDecimal.ONE);
        voucher.setSourceType(command.getSourceType());
        voucher.setSourceId(command.getSourceId());
        voucher.setRemark(command.getRemark());
        List<VoucherLine> lines = new ArrayList<>();
        if (receipt) {
            addLine(voucher, lines, accountProperties.getInventory(), command.getAmount(), null, "生产完工入库");
            addLine(voucher, lines, accountProperties.getProductionCost(), null, command.getAmount(),
                    "生产完工入库");
        } else {
            addLine(voucher, lines, accountProperties.getProductionCost(), command.getAmount(), null, "生产领料");
            addLine(voucher, lines, accountProperties.getInventory(), null, command.getAmount(), "生产领料");
        }
        voucher.setLines(lines);
        voucher.validateAndSum();
        voucher.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(voucher);
        voucher.post(command.getOperatorId(), LocalDateTime.now());
        voucherRepository.update(voucher);
        return voucher.getId();
    }

    /**
     * 生成并记账凭证。
     *
     * @param command 记账命令
     * @param shipment 是否销售发货（否则为采购收货）
     * @return 凭证 ID
     */
    private Long post(AutoVoucherCommand command, boolean shipment) {
        Long existing = existingId(command);
        if (existing != null) {
            return existing;
        }
        Amounts.requirePositive(command.getAmount(), "自动记账金额");
        AccountingPeriod period = requireOpenPeriod(command.getVoucherDate());
        String currency = StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY;
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(command.getVoucherDate());
        voucher.setPeriodCode(period.getPeriodCode());
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);
        voucher.setCurrency(currency);
        voucher.setExchangeRate(BigDecimal.ONE);
        voucher.setSourceType(command.getSourceType());
        voucher.setSourceId(command.getSourceId());
        voucher.setRemark(command.getRemark());
        List<VoucherLine> lines = new ArrayList<>();
        if (shipment) {
            addLine(voucher, lines, accountProperties.getReceivable(), command.getAmount(), null,
                    "确认销售收入");
            addLine(voucher, lines, accountProperties.getRevenue(), null, command.getAmount(),
                    "确认销售收入");
            BigDecimal cost = command.getCostAmount() == null ? BigDecimal.ZERO : command.getCostAmount();
            if (cost.signum() > 0) {
                addLine(voucher, lines, accountProperties.getCost(), cost, null, "结转销售成本");
                addLine(voucher, lines, accountProperties.getInventory(), null, cost, "结转销售成本");
            }
        } else {
            addLine(voucher, lines, accountProperties.getInventory(), command.getAmount(), null,
                    "采购入库");
            addLine(voucher, lines, accountProperties.getPayable(), null, command.getAmount(),
                    "采购入库");
        }
        voucher.setLines(lines);
        voucher.validateAndSum();
        voucher.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(voucher);
        voucher.post(command.getOperatorId(), LocalDateTime.now());
        voucherRepository.update(voucher);
        return voucher.getId();
    }

    /**
     * 查询来源单据已生成的凭证 ID。
     *
     * @param command 记账命令
     * @return 已存在的凭证 ID；不存在返回 null
     */
    private Long existingId(AutoVoucherCommand command) {
        return voucherRepository.findBySource(command.getSourceType(), command.getSourceId())
                .map(Voucher::getId)
                .orElse(null);
    }

    /**
     * 追加一条分录并校验科目可记账。
     *
     * @param voucher 凭证
     * @param lines 分录集合
     * @param accountCode 科目编码
     * @param debit 借方金额
     * @param credit 贷方金额
     * @param summary 摘要
     */
    private void addLine(Voucher voucher, List<VoucherLine> lines, String accountCode,
                         BigDecimal debit, BigDecimal credit, String summary) {
        Account account = accountRepository.findByCode(accountCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ACCOUNT_INVALID,
                        "自动记账科目 " + accountCode + " 不存在"));
        if (!account.postable()) {
            throw ErpErrorCode.exception(ErpErrorCode.ACCOUNT_INVALID,
                    "自动记账科目 " + accountCode + " 已停用或不是末级科目");
        }
        VoucherLine line = new VoucherLine();
        line.setLineNo(lines.size() + 1);
        line.setAccountCode(account.getCode());
        line.setAccountName(account.getName());
        line.setSummary(summary);
        line.setDebitAmount(debit);
        line.setCreditAmount(credit);
        line.setCurrency(voucher.getCurrency());
        line.setExchangeRate(BigDecimal.ONE);
        lines.add(line);
    }

    /**
     * 校验日期所在期间存在且打开。
     *
     * @param voucherDate 凭证日期
     * @return 打开的期间
     */
    private AccountingPeriod requireOpenPeriod(LocalDate voucherDate) {
        LocalDate date = voucherDate == null ? LocalDate.now() : voucherDate;
        String periodCode = date.format(PERIOD_FORMATTER);
        AccountingPeriod period = periodRepository.findByCode(periodCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_DATE_INVALID,
                        "凭证日期 " + date + " 不在任何会计期间内"));
        if (!period.open()) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_CLOSED);
        }
        return period;
    }
}
