package com.bluewhale.erp.finance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.command.ReceivableCreateCommand;
import com.bluewhale.erp.finance.application.command.SettleCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.support.AgingCalculator;
import com.bluewhale.erp.finance.application.view.AgingView;
import com.bluewhale.erp.finance.application.view.CustomerReceivableView;
import com.bluewhale.erp.finance.application.view.PayableView;
import com.bluewhale.erp.finance.application.view.ReceivableView;
import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Payable;
import com.bluewhale.erp.finance.domain.model.PayableSettlement;
import com.bluewhale.erp.finance.domain.model.Receivable;
import com.bluewhale.erp.finance.domain.model.ReceivableSettlement;
import com.bluewhale.erp.finance.domain.repository.PayableRepository;
import com.bluewhale.erp.finance.domain.repository.ReceivableRepository;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 应收应付应用服务实现。
 *
 * <p>M1 支持手工建单、核销、坏账计提与账龄分析；M2 起由采购收货 / 销售发货
 * 自动生成应收应付，届时以 {@code sourceType + sourceId} 做幂等。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ArApAppServiceImpl implements ArApAppService {

    /** 单据号随机后缀长度 */
    private static final int DOC_SUFFIX_LENGTH = 12;

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 应收前缀 */
    private static final String AR_PREFIX = "AR-";

    /** 应付前缀 */
    private static final String AP_PREFIX = "AP-";

    /** 应收账龄维度 */
    private static final String DIMENSION_AR = "AR";

    /** 应付账龄维度 */
    private static final String DIMENSION_AP = "AP";

    private final ReceivableRepository receivableRepository;

    private final PayableRepository payableRepository;

    private final CustomerRepository customerRepository;

    @Override
    public PageResult<ReceivableView> receivablePage(ArApQuery query) {
        List<ReceivableView> records = receivableRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, receivableRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivableView createReceivable(ReceivableCreateCommand command) {
        Optional<Receivable> existing = receivableRepository.findBySource(command.getSourceType(), command.getSourceId());
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.CUSTOMER_NOT_FOUND));
        if (!customer.enabled()) {
            throw ErpErrorCode.exception(ErpErrorCode.CUSTOMER_INVALID);
        }
        Amounts.requirePositive(command.getAmount(), "应收金额");
        validateDates(command.getDocDate(), command.getDueDate());
        String docNo = resolveDocNo(command.getDocNo(), AR_PREFIX,
                value -> receivableRepository.findByDocNo(value).isPresent());
        Receivable receivable = new Receivable();
        receivable.setDocNo(docNo);
        receivable.setCustomerId(command.getCustomerId());
        receivable.setCustomerName(customer.getName());
        receivable.setSourceType(command.getSourceType());
        receivable.setSourceId(command.getSourceId());
        receivable.setDocDate(command.getDocDate());
        receivable.setDueDate(command.getDueDate());
        receivable.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        receivable.setAmount(command.getAmount());
        receivable.setSettledAmount(BigDecimal.ZERO);
        receivable.setBadDebtAmount(BigDecimal.ZERO);
        receivable.setStatus(FinanceStatus.LEDGER_OPEN);
        receivable.setRemark(command.getRemark());
        receivableRepository.insert(receivable);
        return toView(receivable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivableView settleReceivable(Long id, SettleCommand command) {
        Receivable receivable = receivableRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.RECEIVABLE_NOT_FOUND));
        receivable.settle(command.getAmount());
        ReceivableSettlement settlement = new ReceivableSettlement();
        settlement.setReceivableId(id);
        settlement.setSettleDate(command.getSettleDate() == null ? LocalDate.now() : command.getSettleDate());
        settlement.setAmount(command.getAmount());
        settlement.setSettleType(StringUtils.hasText(command.getSettleType())
                ? command.getSettleType() : FinanceStatus.SETTLE_BANK);
        settlement.setReferenceNo(command.getReferenceNo());
        settlement.setRemark(command.getRemark());
        receivableRepository.insertSettlement(settlement);
        receivableRepository.update(receivable);
        return toView(receivable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivableView provisionBadDebt(Long id, SettleCommand command) {
        Receivable receivable = receivableRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.RECEIVABLE_NOT_FOUND));
        receivable.provisionBadDebt(command.getAmount());
        receivableRepository.update(receivable);
        return toView(receivable);
    }

    @Override
    public PageResult<PayableView> payablePage(ArApQuery query) {
        List<PayableView> records = payableRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, payableRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayableView createPayable(PayableCreateCommand command) {
        Optional<Payable> existing = payableRepository.findBySource(command.getSourceType(), command.getSourceId());
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        Amounts.requirePositive(command.getAmount(), "应付金额");
        validateDates(command.getDocDate(), command.getDueDate());
        String docNo = resolveDocNo(command.getDocNo(), AP_PREFIX,
                value -> payableRepository.findByDocNo(value).isPresent());
        Payable payable = new Payable();
        payable.setDocNo(docNo);
        payable.setSupplierId(command.getSupplierId());
        payable.setSupplierName(StringUtils.hasText(command.getSupplierName())
                ? command.getSupplierName() : "供应商-" + command.getSupplierId());
        payable.setSourceType(command.getSourceType());
        payable.setSourceId(command.getSourceId());
        payable.setDocDate(command.getDocDate());
        payable.setDueDate(command.getDueDate());
        payable.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        payable.setAmount(command.getAmount());
        payable.setSettledAmount(BigDecimal.ZERO);
        payable.setStatus(FinanceStatus.LEDGER_OPEN);
        payable.setRemark(command.getRemark());
        payableRepository.insert(payable);
        return toView(payable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayableView settlePayable(Long id, SettleCommand command) {
        Payable payable = payableRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PAYABLE_NOT_FOUND));
        payable.settle(command.getAmount());
        PayableSettlement settlement = new PayableSettlement();
        settlement.setPayableId(id);
        settlement.setSettleDate(command.getSettleDate() == null ? LocalDate.now() : command.getSettleDate());
        settlement.setAmount(command.getAmount());
        settlement.setSettleType(StringUtils.hasText(command.getSettleType())
                ? command.getSettleType() : FinanceStatus.SETTLE_BANK);
        settlement.setReferenceNo(command.getReferenceNo());
        settlement.setRemark(command.getRemark());
        payableRepository.insertSettlement(settlement);
        payableRepository.update(payable);
        return toView(payable);
    }

    @Override
    public List<AgingView> receivableAging() {
        LocalDate today = LocalDate.now();
        Map<Long, AgingView> grouped = new LinkedHashMap<>();
        for (Receivable receivable : receivableRepository.listOutstanding()) {
            AgingView view = grouped.computeIfAbsent(receivable.getCustomerId(),
                    key -> AgingCalculator.empty(DIMENSION_AR, key, receivable.getCustomerName()));
            AgingCalculator.accumulate(view, receivable.remaining(), receivable.overdueDays(today));
        }
        return grouped.values().stream()
                .sorted(Comparator.comparing(AgingView::getTotal).reversed())
                .toList();
    }

    @Override
    public CustomerReceivableView customerReceivable(String customerCode) {
        CustomerReceivableView view = new CustomerReceivableView();
        view.setSnapshotTime(LocalDateTime.now());
        view.setCurrency(DEFAULT_CURRENCY);
        view.setReceivableCount(0);
        view.setTotalRemaining(BigDecimal.ZERO);
        view.setNotDue(BigDecimal.ZERO);
        view.setOverdue1To30(BigDecimal.ZERO);
        view.setOverdue31To60(BigDecimal.ZERO);
        view.setOverdue61To90(BigDecimal.ZERO);
        view.setOverdueOver90(BigDecimal.ZERO);
        if (!StringUtils.hasText(customerCode)) {
            return view;
        }
        Optional<Customer> customer = customerRepository.findByCode(customerCode.trim());
        if (customer.isEmpty()) {
            return view;
        }
        Customer found = customer.get();
        view.setCustomerId(found.getId());
        view.setCustomerCode(found.getCode());
        view.setCustomerName(found.getName());
        List<Receivable> outstanding = receivableRepository.listOutstandingByCustomer(found.getId());
        LocalDate today = LocalDate.now();
        AgingView aging = AgingCalculator.empty(DIMENSION_AR, found.getId(), found.getName());
        for (Receivable receivable : outstanding) {
            AgingCalculator.accumulate(aging, receivable.remaining(), receivable.overdueDays(today));
            if (StringUtils.hasText(receivable.getCurrency())) {
                view.setCurrency(receivable.getCurrency());
            }
            view.getReceivables().add(toView(receivable));
        }
        view.setReceivableCount(outstanding.size());
        view.setTotalRemaining(aging.getTotal());
        view.setNotDue(aging.getNotDue());
        view.setOverdue1To30(aging.getOverdue1To30());
        view.setOverdue31To60(aging.getOverdue31To60());
        view.setOverdue61To90(aging.getOverdue61To90());
        view.setOverdueOver90(aging.getOverdueOver90());
        return view;
    }

    @Override
    public BigDecimal outstandingReceivable(Long customerId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Receivable receivable : receivableRepository.listOutstanding()) {
            if (receivable.getCustomerId() != null && receivable.getCustomerId().equals(customerId)) {
                total = total.add(receivable.remaining());
            }
        }
        return total;
    }

    @Override
    public List<AgingView> payableAging() {
        LocalDate today = LocalDate.now();
        Map<Long, AgingView> grouped = new LinkedHashMap<>();
        for (Payable payable : payableRepository.listOutstanding()) {
            AgingView view = grouped.computeIfAbsent(payable.getSupplierId(),
                    key -> AgingCalculator.empty(DIMENSION_AP, key, payable.getSupplierName()));
            AgingCalculator.accumulate(view, payable.remaining(), payable.overdueDays(today));
        }
        return grouped.values().stream()
                .sorted(Comparator.comparing(AgingView::getTotal).reversed())
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offsetPayable(Long supplierId, BigDecimal amount) {
        Amounts.requirePositive(amount, "退货冲减金额");
        List<Payable> candidates = payableRepository.listOutstanding().stream()
                .filter(payable -> payable.getSupplierId() != null && payable.getSupplierId().equals(supplierId))
                .filter(payable -> payable.remaining().signum() > 0)
                .sorted(Comparator.comparing(Payable::getDocDate).thenComparing(Payable::getId))
                .toList();
        BigDecimal leftover = amount;
        for (Payable payable : candidates) {
            if (leftover.signum() == 0) {
                break;
            }
            BigDecimal allocate = leftover.min(payable.remaining());
            payable.offsetReturn(allocate);
            payableRepository.update(payable);
            leftover = leftover.subtract(allocate);
        }
        if (leftover.signum() > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_OFFSET_EXCEEDED,
                    "未核销余额不足：需冲减 " + amount + "，可用 " + amount.subtract(leftover));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offsetReceivable(Long customerId, BigDecimal amount) {
        Amounts.requirePositive(amount, "退货冲减金额");
        List<Receivable> candidates = receivableRepository.listOutstanding().stream()
                .filter(receivable -> receivable.getCustomerId() != null
                        && receivable.getCustomerId().equals(customerId))
                .filter(receivable -> receivable.remaining().signum() > 0)
                .sorted(Comparator.comparing(Receivable::getDocDate).thenComparing(Receivable::getId))
                .toList();
        BigDecimal leftover = amount;
        for (Receivable receivable : candidates) {
            if (leftover.signum() == 0) {
                break;
            }
            BigDecimal allocate = leftover.min(receivable.remaining());
            receivable.offsetReturn(allocate);
            receivableRepository.update(receivable);
            leftover = leftover.subtract(allocate);
        }
        if (leftover.signum() > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_OFFSET_EXCEEDED,
                    "未核销余额不足：需冲减 " + amount + "，可用 " + amount.subtract(leftover));
        }
    }

    /**
     * 校验到期日不早于单据日期。
     *
     * @param docDate 单据日期
     * @param dueDate 到期日
     */
    private void validateDates(LocalDate docDate, LocalDate dueDate) {
        if (dueDate.isBefore(docDate)) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "到期日不能早于单据日期");
        }
    }

    /**
     * 解析单据号：为空生成，非空校验唯一。
     *
     * @param docNo 命令中的单据号
     * @param prefix 前缀
     * @param exists 单号是否已存在的判断函数
     * @return 最终单据号
     */
    private String resolveDocNo(String docNo, String prefix, Predicate<String> exists) {
        if (StringUtils.hasText(docNo)) {
            if (exists.test(docNo)) {
                throw ErpErrorCode.exception(ErpErrorCode.DOC_NO_CONFLICT);
            }
            return docNo;
        }
        String random = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return prefix + random.substring(0, DOC_SUFFIX_LENGTH);
    }

    /**
     * 应收转视图。
     *
     * @param receivable 领域模型
     * @return 视图
     */
    private ReceivableView toView(Receivable receivable) {
        ReceivableView view = new ReceivableView();
        view.setId(receivable.getId());
        view.setDocNo(receivable.getDocNo());
        view.setCustomerId(receivable.getCustomerId());
        view.setCustomerName(receivable.getCustomerName());
        view.setDocDate(receivable.getDocDate());
        view.setDueDate(receivable.getDueDate());
        view.setCurrency(receivable.getCurrency());
        view.setAmount(receivable.getAmount());
        view.setSettledAmount(receivable.getSettledAmount());
        view.setBadDebtAmount(receivable.getBadDebtAmount());
        view.setRemainingAmount(receivable.remaining());
        view.setStatus(receivable.getStatus());
        view.setRemark(receivable.getRemark());
        return view;
    }

    /**
     * 应付转视图。
     *
     * @param payable 领域模型
     * @return 视图
     */
    private PayableView toView(Payable payable) {
        PayableView view = new PayableView();
        view.setId(payable.getId());
        view.setDocNo(payable.getDocNo());
        view.setSupplierId(payable.getSupplierId());
        view.setSupplierName(payable.getSupplierName());
        view.setDocDate(payable.getDocDate());
        view.setDueDate(payable.getDueDate());
        view.setCurrency(payable.getCurrency());
        view.setAmount(payable.getAmount());
        view.setSettledAmount(payable.getSettledAmount());
        view.setRemainingAmount(payable.remaining());
        view.setStatus(payable.getStatus());
        view.setRemark(payable.getRemark());
        return view;
    }
}
