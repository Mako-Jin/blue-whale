package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.VoucherCreateCommand;
import com.bluewhale.erp.finance.application.command.VoucherLineCommand;
import com.bluewhale.erp.finance.application.service.VoucherAppService;
import com.bluewhale.erp.finance.application.view.VoucherLineView;
import com.bluewhale.erp.finance.application.view.VoucherView;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 凭证应用服务实现。
 *
 * <p>记账前置校验：期间必须打开、科目必须末级且启用、借贷必须平衡。
 * 红冲生成独立反向凭证，原凭证与红冲凭证都会留在账簿中，净额为零。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class VoucherAppServiceImpl implements VoucherAppService {

    /** 期间编码格式 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final VoucherRepository voucherRepository;

    private final AccountRepository accountRepository;

    private final AccountingPeriodRepository periodRepository;

    @Override
    public PageResult<VoucherView> page(VoucherQuery query) {
        List<VoucherView> records = voucherRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, voucherRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public VoucherView detail(Long id) {
        return toView(voucherRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_NOT_FOUND)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoucherView create(VoucherCreateCommand command) {
        AccountingPeriod period = requireOpenPeriod(command.getVoucherDate());
        String currency = command.getCurrency() == null ? DEFAULT_CURRENCY : command.getCurrency();
        BigDecimal rate = command.getExchangeRate() == null ? BigDecimal.ONE : command.getExchangeRate();
        Voucher voucher = new Voucher();
        voucher.setVoucherDate(command.getVoucherDate());
        voucher.setPeriodCode(period.getPeriodCode());
        voucher.setVoucherType(FinanceStatus.TYPE_MANUAL);
        voucher.setStatus(FinanceStatus.VOUCHER_DRAFT);
        voucher.setCurrency(currency);
        voucher.setExchangeRate(rate);
        voucher.setSourceType(command.getSourceType());
        voucher.setSourceId(command.getSourceId());
        voucher.setRemark(command.getRemark());
        List<VoucherLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (VoucherLineCommand lineCommand : command.getLines()) {
            Account account = accountRepository.findByCode(lineCommand.getAccountCode())
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ACCOUNT_INVALID));
            if (!account.postable()) {
                throw ErpErrorCode.exception(ErpErrorCode.ACCOUNT_INVALID,
                        "科目 " + account.getCode() + " 已停用或不是末级科目");
            }
            Amounts.nonNegative(lineCommand.getDebitAmount(), "借方金额");
            Amounts.nonNegative(lineCommand.getCreditAmount(), "贷方金额");
            VoucherLine line = new VoucherLine();
            line.setLineNo(lineNo++);
            line.setAccountCode(account.getCode());
            line.setAccountName(account.getName());
            line.setSummary(lineCommand.getSummary());
            line.setDebitAmount(lineCommand.getDebitAmount());
            line.setCreditAmount(lineCommand.getCreditAmount());
            line.setCurrency(currency);
            line.setExchangeRate(rate);
            lines.add(line);
        }
        voucher.setLines(lines);
        voucher.validateAndSum();
        voucher.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(voucher);
        return toView(voucher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoucherView post(Long id, Long userId) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_NOT_FOUND));
        requireOpenPeriod(voucher.getVoucherDate());
        voucher.post(userId, LocalDateTime.now());
        voucherRepository.update(voucher);
        return toView(voucher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoucherView reverse(Long id, Long userId) {
        Voucher original = voucherRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_NOT_FOUND));
        if (!original.reversible()) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_STATE_CONFLICT, "只有已记账且未红冲的凭证可以红冲");
        }
        AccountingPeriod period = requireOpenPeriod(LocalDate.now());
        Voucher reversal = new Voucher();
        reversal.setVoucherDate(LocalDate.now());
        reversal.setPeriodCode(period.getPeriodCode());
        reversal.setVoucherType(FinanceStatus.TYPE_REVERSAL);
        reversal.setStatus(FinanceStatus.VOUCHER_DRAFT);
        reversal.setCurrency(original.getCurrency());
        reversal.setExchangeRate(original.getExchangeRate());
        reversal.setReversalOfId(original.getId());
        reversal.setRemark("红冲 " + original.getVoucherNo());
        List<VoucherLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (VoucherLine line : original.getLines()) {
            VoucherLine reversalLine = new VoucherLine();
            reversalLine.setLineNo(lineNo++);
            reversalLine.setAccountCode(line.getAccountCode());
            reversalLine.setAccountName(line.getAccountName());
            reversalLine.setSummary("红冲：" + (line.getSummary() == null ? "" : line.getSummary()));
            reversalLine.setDebitAmount(line.credit());
            reversalLine.setCreditAmount(line.debit());
            reversalLine.setCurrency(line.getCurrency());
            reversalLine.setExchangeRate(line.getExchangeRate());
            lines.add(reversalLine);
        }
        reversal.setLines(lines);
        reversal.validateAndSum();
        reversal.setVoucherNo(voucherRepository.nextVoucherNo(period.getPeriodCode()));
        voucherRepository.insert(reversal);
        reversal.post(userId, LocalDateTime.now());
        voucherRepository.update(reversal);
        original.markReversed(reversal.getId());
        voucherRepository.update(original);
        return toView(reversal);
    }

    /**
     * 校验日期所在期间存在且打开。
     *
     * @param voucherDate 凭证日期
     * @return 打开的期间
     */
    private AccountingPeriod requireOpenPeriod(LocalDate voucherDate) {
        String periodCode = voucherDate.format(PERIOD_FORMATTER);
        AccountingPeriod period = periodRepository.findByCode(periodCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.VOUCHER_DATE_INVALID,
                        "凭证日期 " + voucherDate + " 不在任何会计期间内"));
        if (!period.open()) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_CLOSED);
        }
        return period;
    }

    /**
     * 凭证转视图。
     *
     * @param voucher 领域模型
     * @return 视图
     */
    private VoucherView toView(Voucher voucher) {
        VoucherView view = new VoucherView();
        view.setId(voucher.getId());
        view.setVoucherNo(voucher.getVoucherNo());
        view.setVoucherDate(voucher.getVoucherDate());
        view.setPeriodCode(voucher.getPeriodCode());
        view.setVoucherType(voucher.getVoucherType());
        view.setStatus(voucher.getStatus());
        view.setCurrency(voucher.getCurrency());
        view.setExchangeRate(voucher.getExchangeRate());
        view.setTotalDebit(voucher.getTotalDebit());
        view.setTotalCredit(voucher.getTotalCredit());
        view.setSourceType(voucher.getSourceType());
        view.setSourceId(voucher.getSourceId());
        view.setReversalOfId(voucher.getReversalOfId());
        view.setReversedById(voucher.getReversedById());
        view.setRemark(voucher.getRemark());
        view.setPostedAt(voucher.getPostedAt());
        view.setPostedBy(voucher.getPostedBy());
        if (voucher.getLines() != null) {
            view.setLines(voucher.getLines().stream().map(this::toView).toList());
        }
        return view;
    }

    /**
     * 分录转视图。
     *
     * @param line 领域模型
     * @return 视图
     */
    private VoucherLineView toView(VoucherLine line) {
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
}
