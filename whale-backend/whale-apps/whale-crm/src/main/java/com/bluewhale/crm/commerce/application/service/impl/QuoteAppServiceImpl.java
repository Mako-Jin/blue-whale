package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.QuoteApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.QuoteLineCommand;
import com.bluewhale.crm.commerce.application.command.QuoteSaveCommand;
import com.bluewhale.crm.commerce.application.service.QuoteAppService;
import com.bluewhale.crm.commerce.application.view.QuoteView;
import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteApproval;
import com.bluewhale.crm.commerce.domain.model.QuoteApprovalStatus;
import com.bluewhale.crm.commerce.domain.model.QuoteDiscountLevel;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import com.bluewhale.crm.commerce.domain.model.QuoteQuery;
import com.bluewhale.crm.commerce.domain.model.QuoteStatus;
import com.bluewhale.crm.commerce.domain.port.QuoteApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.QuoteApprovalRepository;
import com.bluewhale.crm.commerce.domain.repository.QuoteRepository;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmQuoteApprovalProperties;
import com.bluewhale.crm.sales.application.service.OpportunityAppService;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 报价应用服务实现（M2 增量）。
 *
 * <p>金额口径：行金额 = 单价 × 数量 ×（1 − 行折扣率 / 100）；
 * 汇总 = 行合计 + 税额 − 整单折扣。折扣率按折前未税金额计算，用于选择审批层级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAppServiceImpl implements QuoteAppService {

    /** 报价编号前缀 */
    private static final String QUOTE_PREFIX = "QUOTE";

    /** 年份格式 */
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    /** 序号长度 */
    private static final int SEQUENCE_LENGTH = 6;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 默认税率 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13");

    /** 回调状态：通过 */
    private static final int CALLBACK_APPROVED = 1;

    /** 回调状态：驳回 */
    private static final int CALLBACK_REJECTED = 2;

    /** 回调状态：撤销 */
    private static final int CALLBACK_CANCELLED = 3;

    private final QuoteRepository quoteRepository;

    private final QuoteApprovalRepository approvalRepository;

    private final OpportunityAppService opportunityAppService;

    private final QuoteApprovalPort approvalPort;

    private final CrmProperties properties;

    private final CrmQuoteApprovalProperties approvalProperties;

    @Override
    public PageResult<QuoteView> page(QuoteQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<Quote> quotes = quoteRepository.page(query);
        long total = quoteRepository.count(query);
        List<QuoteView> views = quotes.stream().map(quote -> QuoteView.from(quote, null)).toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public QuoteView detail(Long id, CrmActor actor) {
        assertGate();
        Quote quote = requireQuote(id, actor);
        return QuoteView.from(quote, quoteRepository.findLines(quote.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteView create(QuoteSaveCommand command, CrmActor actor) {
        assertGate();
        requireBusinessCommand(command);
        OpportunityView opportunity = opportunityAppService.detail(command.getOpportunityId(), actor);
        if (opportunity.getCustomerId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_CUSTOMER_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        List<QuoteLine> lines = buildLines(command.getLines());
        Totals totals = calculate(lines, command.getDiscountAmount());
        Quote quote = new Quote();
        quote.setOpportunityId(opportunity.getId());
        quote.setCustomerId(opportunity.getCustomerId());
        quote.setQuoteNo(nextQuoteNo());
        quote.setVersion(1);
        quote.setTitle(command.getTitle().trim());
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setCurrency(resolveCurrency(command.getCurrency()));
        quote.setValidUntil(command.getValidUntil());
        quote.setRemark(trimToNull(command.getRemark()));
        quote.setOwnerId(actor.requireUserId());
        quote.setDeptId(opportunity.getDeptId());
        quote.updateAmounts(totals.subtotal, totals.tax, totals.discount, totals.total, totals.rate);
        quote.setCreateTime(now);
        quote.setUpdateTime(now);
        quoteRepository.insert(quote);
        quoteRepository.bindRoot(quote.getId(), quote.getId());
        quote.setRootQuoteId(quote.getId());
        quoteRepository.replaceLines(quote.getId(), lines);
        return QuoteView.from(quote, lines);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteView update(Long id, QuoteSaveCommand command, CrmActor actor) {
        assertGate();
        requireBusinessCommand(command);
        Quote quote = requireQuote(id, actor);
        quote.requireEditable();
        if (command.getOpportunityId() != null && !Objects.equals(command.getOpportunityId(), quote.getOpportunityId())) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "报价创建后不可更换商机");
        }
        List<QuoteLine> lines = buildLines(command.getLines());
        Totals totals = calculate(lines, command.getDiscountAmount());
        quote.setTitle(command.getTitle().trim());
        quote.setCurrency(resolveCurrency(command.getCurrency()));
        quote.setValidUntil(command.getValidUntil());
        quote.setRemark(trimToNull(command.getRemark()));
        quote.updateAmounts(totals.subtotal, totals.tax, totals.discount, totals.total, totals.rate);
        quote.setUpdateTime(LocalDateTime.now());
        if (!quoteRepository.update(quote)) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "报价已被他人修改，请刷新后重试");
        }
        quoteRepository.replaceLines(quote.getId(), lines);
        return QuoteView.from(quote, lines);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteView submit(Long id, CrmActor actor) {
        assertGate();
        Quote quote = requireQuote(id, actor);
        quote.requireEditable();
        if (!approvalProperties.isEnabled() || approvalProperties.getDefinitionId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_APPROVAL_UNAVAILABLE);
        }
        BigDecimal rate = quote.getDiscountRate() == null ? BigDecimal.ZERO : quote.getDiscountRate();
        String level = QuoteDiscountLevel.resolve(rate).name();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("quoteId", quote.getId());
        variables.put("quoteNo", quote.getQuoteNo());
        variables.put("version", quote.getVersion());
        variables.put("opportunityId", quote.getOpportunityId());
        variables.put("customerId", quote.getCustomerId());
        variables.put("totalAmount", quote.getTotalAmount());
        variables.put("discountRate", rate);
        variables.put("approvalLevel", level);
        variables.put("applicant", actor.getUsername());
        Long instanceId;
        try {
            instanceId = approvalPort.start(approvalProperties.getDefinitionId(),
                    "报价审批：" + quote.getTitle(),
                    "quote:" + quote.getId() + ":v" + quote.getVersion(),
                    variables, actor);
        } catch (Exception exception) {
            log.warn("报价审批发起失败 quoteId={}", quote.getId(), exception);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.failed-start-quotation-approval-retry-later", "报价审批发起失败，请稍后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        quote.submit(instanceId, rate, now);
        quote.setUpdateTime(now);
        if (!quoteRepository.update(quote)) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "报价已被他人修改，请刷新后重试");
        }
        QuoteApproval approval = new QuoteApproval();
        approval.setQuoteId(quote.getId());
        approval.setQuoteVersion(quote.getVersion());
        approval.setInstanceId(instanceId);
        approval.setStatus(QuoteApprovalStatus.PENDING);
        approval.setApprovalLevel(level);
        approval.setDiscountRate(rate);
        approval.setSubmitterId(actor.requireUserId());
        approval.setSubmittedAt(now);
        approvalRepository.insert(approval);
        return QuoteView.from(quote, quoteRepository.findLines(quote.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteView revise(Long id, CrmActor actor) {
        assertGate();
        Quote source = requireQuote(id, actor);
        LocalDateTime now = LocalDateTime.now();
        Quote next = source.nextVersion(now);
        List<QuoteLine> sourceLines = quoteRepository.findLines(source.getId());
        quoteRepository.insert(next);
        List<QuoteLine> copies = new ArrayList<>();
        for (QuoteLine sourceLine : sourceLines) {
            QuoteLine copy = new QuoteLine();
            copy.setQuoteId(next.getId());
            copy.setLineNo(sourceLine.getLineNo());
            copy.setProductName(sourceLine.getProductName());
            copy.setSpecification(sourceLine.getSpecification());
            copy.setUnit(sourceLine.getUnit());
            copy.setQuantity(sourceLine.getQuantity());
            copy.setUnitPrice(sourceLine.getUnitPrice());
            copy.setDiscountRate(sourceLine.getDiscountRate());
            copy.setTaxRate(sourceLine.getTaxRate());
            copy.setLineAmount(sourceLine.getLineAmount());
            copy.setRemark(sourceLine.getRemark());
            copies.add(copy);
        }
        quoteRepository.replaceLines(next.getId(), copies);
        return QuoteView.from(next, copies);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalCallback(QuoteApprovalCallbackCommand command) {
        if (command == null || command.getInstanceId() == null || command.getStatus() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_APPROVAL_STATE_CONFLICT);
        }
        int status = command.getStatus();
        if (status != CALLBACK_APPROVED && status != CALLBACK_REJECTED && status != CALLBACK_CANCELLED) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_APPROVAL_STATE_CONFLICT);
        }
        QuoteApproval approval = approvalRepository.findPendingByInstanceId(command.getInstanceId());
        if (approval == null) {
            return false;
        }
        Quote quote = quoteRepository.findById(approval.getQuoteId());
        if (quote == null) {
            return false;
        }
        boolean approved = status == CALLBACK_APPROVED;
        LocalDateTime now = LocalDateTime.now();
        if (quote.getStatus() == QuoteStatus.PENDING_APPROVAL) {
            quote.applyApproval(approved, now);
            quote.setUpdateTime(now);
            if (!quoteRepository.update(quote)) {
                return false;
            }
        } else {
            QuoteStatus expected = approved ? QuoteStatus.APPROVED : QuoteStatus.REJECTED;
            if (quote.getStatus() != expected) {
                return false;
            }
        }
        approvalRepository.markDecided(approval.getId(),
                approved ? QuoteApprovalStatus.APPROVED : QuoteApprovalStatus.REJECTED,
                trimToNull(command.getComment()), now);
        return true;
    }

    /**
     * 校验业务门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 校验新建 / 编辑命令的最小业务字段。
     *
     * @param command 保存命令
     */
    private void requireBusinessCommand(QuoteSaveCommand command) {
        if (command == null || command.getOpportunityId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "报价必须关联商机");
        }
        if (command.getTitle() == null || command.getTitle().isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "报价标题不能为空");
        }
        if (command.getLines() == null || command.getLines().isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_LINE_REQUIRED);
        }
    }

    /**
     * 应用数据范围。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyDataScope(QuoteQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
    }

    /**
     * 加载报价并校验数据范围。
     *
     * @param id 报价 ID
     * @param actor 当前操作人
     * @return 报价
     */
    private Quote requireQuote(Long id, CrmActor actor) {
        Quote quote = quoteRepository.findById(id);
        if (quote == null) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(quote.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_NOT_FOUND);
        }
        return quote;
    }

    /**
     * 生成报价编号。
     *
     * @return 报价编号
     */
    private String nextQuoteNo() {
        String prefix = QUOTE_PREFIX + "-" + LocalDateTime.now().format(YEAR_FORMAT) + "-";
        long sequence = quoteRepository.maxSequence(prefix) + 1;
        return prefix + String.format(Locale.ROOT, "%0" + SEQUENCE_LENGTH + "d", sequence);
    }

    /**
     * 构建报价行。
     *
     * @param commands 行命令
     * @return 报价行
     */
    private List<QuoteLine> buildLines(List<QuoteLineCommand> commands) {
        List<QuoteLine> lines = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            QuoteLineCommand command = commands.get(index);
            if (command == null || command.getProductName() == null || command.getProductName().isBlank()) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "产品名称不能为空");
            }
            QuoteLine line = new QuoteLine();
            line.setLineNo(index + 1);
            line.setProductName(command.getProductName().trim());
            line.setSpecification(trimToNull(command.getSpecification()));
            line.setUnit(trimToNull(command.getUnit()));
            line.setQuantity(command.getQuantity());
            line.setUnitPrice(command.getUnitPrice());
            line.setDiscountRate(command.getDiscountRate() == null ? BigDecimal.ZERO : command.getDiscountRate());
            line.setTaxRate(command.getTaxRate() == null ? DEFAULT_TAX_RATE : command.getTaxRate());
            line.setRemark(trimToNull(command.getRemark()));
            lines.add(line);
        }
        return lines;
    }

    /**
     * 计算汇总金额与综合折扣率。
     *
     * @param lines 报价行
     * @param discountAmount 整单折扣金额
     * @return 汇总结果
     */
    private Totals calculate(List<QuoteLine> lines, BigDecimal discountAmount) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (QuoteLine line : lines) {
            validateLine(line);
            BigDecimal lineAmount = line.calculateAmount();
            line.setLineAmount(lineAmount);
            gross = gross.add(line.getUnitPrice().multiply(line.getQuantity()));
            subtotal = subtotal.add(lineAmount);
            tax = tax.add(lineTax(line));
        }
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discount.signum() < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "整单折扣不能为负数");
        }
        if (discount.compareTo(subtotal.add(tax)) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "整单折扣不得超过行合计加税额");
        }
        BigDecimal total = subtotal.add(tax).subtract(discount);
        if (total.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "报价含税总价必须大于 0");
        }
        return new Totals(subtotal, tax, discount, total, resolveRate(gross, subtotal, discount));
    }

    /**
     * 校验报价行金额参数。
     *
     * @param line 报价行
     */
    private void validateLine(QuoteLine line) {
        if (line.getQuantity() == null || line.getQuantity().signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "报价行数量必须大于 0");
        }
        if (line.getUnitPrice() == null || line.getUnitPrice().signum() < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "报价行单价不能为负数");
        }
        if (line.getDiscountRate().signum() < 0
                || line.getDiscountRate().compareTo(HUNDRED) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "行折扣率必须在 0–100 之间");
        }
        if (line.getTaxRate().signum() < 0 || line.getTaxRate().compareTo(HUNDRED) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_AMOUNT_INVALID, "税率必须在 0–100 之间");
        }
    }

    /**
     * 计算行税额。
     *
     * @param line 报价行
     * @return 行税额
     */
    private BigDecimal lineTax(QuoteLine line) {
        return line.getLineAmount().multiply(line.getTaxRate())
                .divide(HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算综合折扣率。
     *
     * @param gross 折前未税金额
     * @param subtotal 行合计
     * @param discount 整单折扣
     * @return 综合折扣率
     */
    private BigDecimal resolveRate(BigDecimal gross, BigDecimal subtotal, BigDecimal discount) {
        if (gross.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = gross.subtract(subtotal).add(discount)
                .divide(gross, AMOUNT_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return rate.signum() < 0 ? BigDecimal.ZERO : rate;
    }

    /**
     * 解析币种。
     *
     * @param currency 币种
     * @return 非空币种
     */
    private String resolveCurrency(String currency) {
        return currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 去空白并转空串为 {@code null}。
     *
     * @param value 原始值
     * @return 处理后的值
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 报价汇总结果。
     *
     * @param subtotal 行合计
     * @param tax 税额
     * @param discount 整单折扣
     * @param total 含税总价
     * @param rate 综合折扣率
     */
    private record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal discount,
                          BigDecimal total, BigDecimal rate) {
    }
}
