package com.bluewhale.oa.expense.application.service.impl;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.AttachmentOwnershipGuard;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import com.bluewhale.oa.expense.domain.gateway.ErpPayableGateway;
import com.bluewhale.oa.expense.domain.model.ErpPayableRequest;
import com.bluewhale.oa.expense.domain.model.ErpPushStatus;
import com.bluewhale.oa.expense.domain.model.Expense;
import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseInvoice;
import com.bluewhale.oa.expense.domain.model.ExpenseItem;
import com.bluewhale.oa.expense.domain.model.ExpenseQuery;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;
import com.bluewhale.oa.expense.domain.model.ExpenseStatus;
import com.bluewhale.oa.expense.domain.repository.ExpenseCategoryRepository;
import com.bluewhale.oa.expense.domain.repository.ExpenseRepository;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.application.service.OutboxAppService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报销单应用服务实现。
 *
 * <p>合计金额由服务端按明细重算；提交时校验发票全局唯一与费用标准。
 * 审批通过后在同一事务内追加 Outbox {@code OA_EXPENSE_APPROVED}，
 * ERP 应付推送由调度任务或手工重试触发，失败不阻断审批通过。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseAppServiceImpl implements ExpenseAppService {

    /** 报销业务类型 */
    private static final String BIZ_TYPE_EXPENSE = "EXPENSE";

    /** 单号业务类型 */
    private static final String CODE_BIZ_TYPE = "EXPENSE";

    /** 单号前缀 */
    private static final String CODE_PREFIX = "EXP";

    /** ERP 审批通过事件类型 */
    private static final String EVENT_TYPE_EXPENSE_APPROVED = "OA_EXPENSE_APPROVED";

    /** 聚合类型 */
    private static final String AGGREGATE_TYPE_EXPENSE = "EXPENSE";

    /** ERP 来源类型 */
    private static final String ERP_SOURCE_TYPE = "OA_EXPENSE";

    /** 默认费用标准适用范围 */
    private static final String DEFAULT_LEVEL_CODE = "DEFAULT";

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 单次推送上限 */
    private static final int MAX_PUSH_BATCH = 50;

    /** 金额标度 */
    private static final int AMOUNT_SCALE = 2;

    private final ExpenseRepository expenseRepository;

    private final ExpenseCategoryRepository categoryRepository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final OutboxAppService outboxAppService;

    private final SequenceAppService sequenceAppService;

    private final ErpPayableGateway erpPayableGateway;

    private final AttachmentOwnershipGuard attachmentOwnershipGuard;

    private final TripRepository tripRepository;

    /** 独立 JSON 解析器：支持 Java 时间类型 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public PageResult<ExpenseViews.ExpenseView> page(ExpenseQuery query) {
        List<ExpenseViews.ExpenseView> records = expenseRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, expenseRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public ExpenseViews.ExpenseView detail(Long id) {
        return toView(requireDetail(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseViews.ExpenseView create(ExpenseCommands.ExpenseSaveCommand command) {
        Expense expense = new Expense();
        applyHeader(expense, command);
        expense.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        assertCodeUnique(expense.getCode(), null);
        expense.setItems(buildItems(command.getItems()));
        expense.setInvoices(buildInvoices(command.getInvoices(), null));
        expense.setTotalAmount(sumItems(expense.getItems()));
        assertInvoiceAmount(expense);
        expense.setStatus(ExpenseStatus.PENDING);
        expense.setErpPushStatus(ErpPushStatus.NONE);
        expense.setSubmitTime(LocalDateTime.now());
        expenseRepository.insert(expense);
        expenseRepository.replaceItems(expense.getId(), expense.getItems());
        expenseRepository.replaceInvoices(expense.getId(), expense.getInvoices());
        registerApproval(expense);
        log.info("报销单已创建: id={}, code={}, amount={}", expense.getId(), expense.getCode(),
                expense.getTotalAmount());
        return toView(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseViews.ExpenseView update(Long id, ExpenseCommands.ExpenseSaveCommand command) {
        Expense expense = requireEditable(id);
        applyHeader(expense, command);
        assertCodeUnique(expense.getCode(), id);
        expense.setItems(buildItems(command.getItems()));
        expense.setInvoices(buildInvoices(command.getInvoices(), id));
        expense.setTotalAmount(sumItems(expense.getItems()));
        assertInvoiceAmount(expense);
        expenseRepository.update(expense);
        expenseRepository.replaceItems(id, expense.getItems());
        expenseRepository.replaceInvoices(id, expense.getInvoices());
        log.info("报销单已更新: id={}, amount={}", id, expense.getTotalAmount());
        return toView(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Expense expense = requireEditable(id);
        expenseRepository.deleteById(expense.getId());
        log.info("报销单已删除: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseViews.ExpenseView submit(Long id) {
        Expense expense = requireExpense(id);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "仅待审批的报销单可提交");
        }
        registerApproval(expense);
        return toView(expense);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, ExpenseCommands.ExpenseStatusCommand command) {
        Expense expense = requireExpense(id);
        ExpenseStatus target = ExpenseStatus.of(command.getStatus());
        if (!expense.changeStatus(target)) {
            log.info("报销单状态未变化，幂等返回: id={}, status={}", id, target);
            return;
        }
        if (target == ExpenseStatus.APPROVED) {
            expense.setErpPushStatus(ErpPushStatus.PENDING);
        }
        expenseRepository.update(expense);
        if (target == ExpenseStatus.APPROVED) {
            appendApprovedEvent(expense);
        }
        log.info("报销单状态已变更: id={}, status={}", id, expense.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long expenseId, String result) {
        ExpenseStatus target = ExpenseStatus.of(result);
        if (target == ExpenseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        ExpenseCommands.ExpenseStatusCommand command = new ExpenseCommands.ExpenseStatusCommand();
        command.setStatus(target.name());
        updateStatus(expenseId, command);
    }

    @Override
    public int pushPending() {
        List<Expense> pending = expenseRepository.listPushable(MAX_PUSH_BATCH);
        int success = 0;
        for (Expense expense : pending) {
            try {
                pushOne(expense);
                success++;
            } catch (RuntimeException exception) {
                log.warn("报销单 ERP 推送失败: id={}, error={}", expense.getId(), exception.getMessage());
            }
        }
        return success;
    }

    @Override
    public ExpenseViews.ExpenseView retryPush(Long id) {
        Expense expense = requireExpense(id);
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "仅审批通过的报销单可推送 ERP");
        }
        pushOne(expense);
        return toView(expense);
    }

    /**
     * 推送单张报销单到 ERP：成功回写应付单号，失败标记 FAILED 并抛出异常。
     *
     * @param expense 报销单
     */
    private void pushOne(Expense expense) {
        if (expense.getErpPushStatus() == ErpPushStatus.SENT) {
            return;
        }
        try {
            String payableNo = erpPayableGateway.createPayable(buildPayable(expense));
            expense.setErpPayableNo(payableNo);
            expense.setErpPushStatus(ErpPushStatus.SENT);
            expenseRepository.update(expense);
            log.info("报销单 ERP 应付已创建: id={}, payableNo={}", expense.getId(), payableNo);
        } catch (RuntimeException exception) {
            expense.setErpPushStatus(ErpPushStatus.FAILED);
            expenseRepository.update(expense);
            throw exception;
        }
    }

    /**
     * 构造 ERP 应付单请求。
     *
     * @param expense 报销单
     * @return 应付单请求
     */
    private ErpPayableRequest buildPayable(Expense expense) {
        ErpPayableRequest request = new ErpPayableRequest();
        request.setSourceType(ERP_SOURCE_TYPE);
        request.setSourceId(expense.getId());
        request.setSourceCode(expense.getCode());
        request.setApplicantId(expense.getApplicantId());
        request.setDeptId(expense.getDeptId());
        request.setCategoryCode(expense.getCategoryCode());
        request.setAmount(expense.getTotalAmount());
        request.setCurrency(expense.getCurrency());
        request.setTitle(expense.getTitle());
        return request;
    }

    /**
     * 登记审批实例（已登记则跳过），并回写审批实例ID。
     *
     * @param expense 报销单
     */
    private void registerApproval(Expense expense) {
        if (expense.getApprovalInstanceId() != null) {
            return;
        }
        ApprovalInstanceView instance = approvalInstanceAppService.register(registerCommand(expense));
        expense.setApprovalInstanceId(instance.getId());
        expenseRepository.update(expense);
    }

    /**
     * 构造审批实例登记命令。
     *
     * @param expense 报销单
     * @return 登记命令
     */
    private ApprovalInstanceRegisterCommand registerCommand(Expense expense) {
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType(BIZ_TYPE_EXPENSE);
        command.setBusinessId(expense.getId());
        command.setBusinessCode(expense.getCode());
        command.setTitle(expense.getTitle());
        command.setInitiatorId(expense.getApplicantId());
        command.setInitiatorName(expense.getApplicant());
        command.setRemark(expense.getRemark());
        return command;
    }

    /**
     * 追加审批通过 Outbox 事件（幂等键 expense.id）。
     *
     * @param expense 报销单
     */
    private void appendApprovedEvent(Expense expense) {
        OutboxAppendCommand command = new OutboxAppendCommand();
        command.setEventKey("expense:" + expense.getId());
        command.setEventType(EVENT_TYPE_EXPENSE_APPROVED);
        command.setAggregateType(AGGREGATE_TYPE_EXPENSE);
        command.setAggregateId(String.valueOf(expense.getId()));
        command.setPayload(payload(expense));
        outboxAppService.append(command);
    }

    /**
     * 序列化 Outbox 事件载荷。
     *
     * @param expense 报销单
     * @return JSON 载荷（序列化失败返回 null）
     */
    private String payload(Expense expense) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expenseId", expense.getId());
        data.put("code", expense.getCode());
        data.put("applicantId", expense.getApplicantId());
        data.put("deptId", expense.getDeptId());
        data.put("categoryCode", expense.getCategoryCode());
        data.put("totalAmount", expense.getTotalAmount());
        data.put("currency", expense.getCurrency());
        data.put("approvedAt", LocalDateTime.now());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            log.warn("报销审批通过事件序列化失败: id={}", expense.getId());
            return null;
        }
    }

    /**
     * 汇总明细金额（2 位小数）。
     *
     * @param items 明细列表
     * @return 合计金额
     */
    private BigDecimal sumItems(List<ExpenseItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseItem item : items) {
            total = total.add(item.getAmount());
        }
        if (total.signum() <= 0) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVALID, "报销合计金额必须大于 0");
        }
        return total.setScale(AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 校验发票合计不超过明细合计。
     *
     * @param expense 报销单
     */
    private void assertInvoiceAmount(Expense expense) {
        BigDecimal invoiceTotal = BigDecimal.ZERO;
        for (ExpenseInvoice invoice : expense.getInvoices()) {
            invoiceTotal = invoiceTotal.add(invoice.getAmount());
        }
        if (invoiceTotal.compareTo(expense.getTotalAmount()) > 0) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVALID, "发票合计金额不得超过报销明细合计");
        }
    }

    /**
     * 构造并校验报销明细（类别存在、标准未超限）。
     *
     * @param commands 明细命令
     * @return 明细列表
     */
    private List<ExpenseItem> buildItems(List<ExpenseCommands.ExpenseItemCommand> commands) {
        List<ExpenseItem> items = new ArrayList<>();
        int lineNo = 1;
        for (ExpenseCommands.ExpenseItemCommand command : commands) {
            ExpenseCategory category = requireCategory(command.getCategoryCode());
            assertStandard(category, command.getAmount());
            ExpenseItem item = new ExpenseItem();
            item.setLineNo(lineNo++);
            item.setCategoryCode(category.getCode());
            item.setExpenseDate(command.getExpenseDate());
            item.setAmount(command.getAmount() == null ? BigDecimal.ZERO : command.getAmount());
            item.setTaxAmount(command.getTaxAmount() == null ? BigDecimal.ZERO : command.getTaxAmount());
            item.setDescription(command.getDescription());
            items.add(item);
        }
        return items;
    }

    /**
     * 构造并校验报销发票（本单与跨单均不可重复）。
     *
     * @param commands 发票命令，可为空
     * @param excludeExpenseId 更新时排除的报销单ID
     * @return 发票列表
     */
    private List<ExpenseInvoice> buildInvoices(List<ExpenseCommands.ExpenseInvoiceCommand> commands,
                                               Long excludeExpenseId) {
        List<ExpenseInvoice> invoices = new ArrayList<>();
        if (commands == null) {
            return invoices;
        }
        Set<String> seen = new HashSet<>();
        for (ExpenseCommands.ExpenseInvoiceCommand command : commands) {
            String key = nullToEmpty(command.getInvoiceCode()) + "|" + command.getInvoiceNo();
            if (!seen.add(key)) {
                throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVOICE_DUPLICATE,
                        "发票在本单中重复录入：" + command.getInvoiceNo());
            }
            if (expenseRepository.existsInvoice(command.getInvoiceCode(), command.getInvoiceNo(),
                    excludeExpenseId)) {
                throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVOICE_DUPLICATE,
                        "发票已被其他报销单使用：" + command.getInvoiceNo());
            }
            ExpenseInvoice invoice = new ExpenseInvoice();
            invoice.setInvoiceCode(command.getInvoiceCode());
            invoice.setInvoiceNo(command.getInvoiceNo());
            invoice.setInvoiceDate(command.getInvoiceDate());
            invoice.setAmount(command.getAmount() == null ? BigDecimal.ZERO : command.getAmount());
            invoice.setTaxAmount(command.getTaxAmount() == null ? BigDecimal.ZERO : command.getTaxAmount());
            attachmentOwnershipGuard.assertReferable(command.getFileId(), RequestContextHolder.getUserId());
            invoice.setFileId(command.getFileId());
            invoices.add(invoice);
        }
        return invoices;
    }

    /**
     * 校验费用类别存在且启用。
     *
     * @param code 类别编码
     * @return 费用类别
     */
    private ExpenseCategory requireCategory(String code) {
        ExpenseCategory category = categoryRepository.findCategoryByCode(code)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                        "费用类别不存在：" + code));
        if (Boolean.FALSE.equals(category.getEnabled())) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                    "费用类别已停用：" + code);
        }
        return category;
    }

    /**
     * 校验单笔金额不超过类别默认标准。
     *
     * @param category 费用类别
     * @param amount 明细金额
     */
    private void assertStandard(ExpenseCategory category, BigDecimal amount) {
        ExpenseStandard standard = categoryRepository
                .findStandard(category.getId(), DEFAULT_LEVEL_CODE).orElse(null);
        if (standard == null || Boolean.FALSE.equals(standard.getEnabled())) {
            return;
        }
        BigDecimal limit = standard.getLimitAmount();
        if (limit != null && amount != null && amount.compareTo(limit) > 0) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STANDARD_EXCEEDED,
                    "费用 " + category.getCode() + " 单笔金额超过标准 " + limit.toPlainString() + " 元");
        }
    }

    /**
     * 校验单号在租户内唯一。
     *
     * @param code 报销单号
     * @param excludeId 更新时排除自身ID
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (expenseRepository.existsByCode(code, excludeId)) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVALID, "报销单号已存在");
        }
    }

    /**
     * 查询报销单（仅单头），缺失时抛业务异常。
     *
     * @param id 主键ID
     * @return 报销单
     */
    private Expense requireExpense(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_NOT_FOUND,
                        "报销单不存在，id=" + id));
    }

    /**
     * 查询报销单详情（含明细与发票）。
     *
     * @param id 主键ID
     * @return 报销单
     */
    private Expense requireDetail(Long id) {
        Expense expense = requireExpense(id);
        expense.setItems(expenseRepository.listItems(id));
        expense.setInvoices(expenseRepository.listInvoices(id));
        return expense;
    }

    /**
     * 查询并校验报销单处于可编辑状态。
     *
     * @param id 主键ID
     * @return 报销单
     */
    private Expense requireEditable(Long id) {
        Expense expense = requireExpense(id);
        expense.ensureEditable();
        return expense;
    }

    /**
     * 命令字段写入报销单。
     *
     * @param expense 报销单
     * @param command 保存命令
     */
    private void applyHeader(Expense expense, ExpenseCommands.ExpenseSaveCommand command) {
        expense.setApplicant(command.getApplicant());
        expense.setApplicantId(command.getApplicantId());
        expense.setDeptId(command.getDeptId());
        expense.setCategoryCode(command.getCategoryCode());
        expense.setTitle(command.getTitle());
        expense.setCurrency(StringUtils.hasText(command.getCurrency())
                ? command.getCurrency() : DEFAULT_CURRENCY);
        expense.setRemark(command.getRemark());
        applyTrip(expense, command.getTripId());
    }

    /**
     * 关联出差单并快照预算：未关联时清空关联并置预算为 0。
     *
     * @param expense 报销单
     * @param tripId 出差单ID，可为空
     */
    private void applyTrip(Expense expense, Long tripId) {
        if (tripId == null) {
            expense.setTripId(null);
            expense.setTripBudgetAmount(BigDecimal.ZERO);
            return;
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.TRIP_NOT_FOUND,
                        "出差申请不存在，id=" + tripId));
        expense.setTripId(trip.getId());
        expense.setTripBudgetAmount(trip.getBudgetAmount() == null
                ? BigDecimal.ZERO : trip.getBudgetAmount());
    }

    /**
     * 空值转空串。
     *
     * @param value 文本
     * @return 非空文本或空串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 领域模型转视图。
     *
     * @param expense 报销单
     * @return 报销单视图
     */
    private ExpenseViews.ExpenseView toView(Expense expense) {
        ExpenseViews.ExpenseView view = new ExpenseViews.ExpenseView();
        view.setId(expense.getId());
        view.setCode(expense.getCode());
        view.setApplicant(expense.getApplicant());
        view.setApplicantId(expense.getApplicantId());
        view.setDeptId(expense.getDeptId());
        view.setCategoryCode(expense.getCategoryCode());
        view.setTitle(expense.getTitle());
        view.setTotalAmount(expense.getTotalAmount());
        view.setCurrency(expense.getCurrency());
        view.setStatus(expense.getStatus() == null ? null : expense.getStatus().name());
        view.setApprovalInstanceId(expense.getApprovalInstanceId());
        view.setErpPushStatus(expense.getErpPushStatus() == null
                ? null : expense.getErpPushStatus().name());
        view.setErpPayableNo(expense.getErpPayableNo());
        view.setTripId(expense.getTripId());
        view.setTripBudgetAmount(expense.getTripBudgetAmount());
        view.setSubmitTime(expense.getSubmitTime());
        view.setRemark(expense.getRemark());
        view.setCreateTime(expense.getCreateTime());
        view.setUpdateTime(expense.getUpdateTime());
        view.setItems(expense.getItems().stream().map(ExpenseViews.ItemView::from).toList());
        view.setInvoices(expense.getInvoices().stream().map(ExpenseViews.InvoiceView::from).toList());
        view.setApprovalVariables(approvalVariables(expense));
        return view;
    }

    /**
     * 构造审批条件变量（预算超限分支使用）。
     *
     * @param expense 报销单
     * @return 条件变量
     */
    private Map<String, Object> approvalVariables(Expense expense) {
        BigDecimal total = expense.getTotalAmount() == null ? BigDecimal.ZERO : expense.getTotalAmount();
        BigDecimal budget = expense.getTripBudgetAmount() == null
                ? BigDecimal.ZERO : expense.getTripBudgetAmount();
        boolean overrun = budget.signum() > 0 && total.compareTo(budget) > 0;
        BigDecimal overrunAmount = overrun
                ? total.subtract(budget).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("amount", expense.getTotalAmount());
        variables.put("categoryCode", expense.getCategoryCode());
        variables.put("deptId", expense.getDeptId());
        variables.put("tripId", expense.getTripId());
        variables.put("budgetAmount", budget);
        variables.put("budgetOverrun", overrun);
        variables.put("budgetOverrunAmount", overrunAmount);
        return variables;
    }
}
