package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.SalesOrderApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderCreateCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderUpdateCommand;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.commerce.application.view.SalesOrderView;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.OrderOutboxEvent;
import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import com.bluewhale.crm.commerce.domain.model.QuoteStatus;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import com.bluewhale.crm.commerce.domain.model.SalesOrderQuery;
import com.bluewhale.crm.commerce.domain.model.SalesOrderStatus;
import com.bluewhale.crm.commerce.domain.port.ErpOrderCommand;
import com.bluewhale.crm.commerce.domain.port.ErpOrderPort;
import com.bluewhale.crm.commerce.domain.port.ErpOrderResult;
import com.bluewhale.crm.commerce.domain.port.OrderApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.commerce.domain.repository.OrderOutboxRepository;
import com.bluewhale.crm.commerce.domain.repository.OrderPushLogRepository;
import com.bluewhale.crm.commerce.domain.repository.QuoteRepository;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 商务订单应用服务实现。
 *
 * <p>审批通过后在同一事务写入 outbox，由定时任务幂等下发 ERP；失败按指数退避重试，
 * 并按 CRM 单号对账回读履约状态，避免超时重复建单。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class SalesOrderAppServiceImpl implements SalesOrderAppService {

    /** 来源类型：手工 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 来源类型：报价 */
    private static final String SOURCE_QUOTE = "QUOTE";

    /** 来源类型：合同 */
    private static final String SOURCE_CONTRACT = "CONTRACT";

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 订单编号前缀 */
    private static final String ORDER_NO_PREFIX = "SO-";

    /** 订单编号日期格式 */
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 退避最大指数 */
    private static final int MAX_BACKOFF_EXPONENT = 16;

    /** 达到最大尝试次数后的静默间隔（天） */
    private static final long STOP_RETRY_DAYS = 30L;

    private final SalesOrderRepository salesOrderRepository;

    private final OrderOutboxRepository orderOutboxRepository;

    private final QuoteRepository quoteRepository;

    private final ContractLedgerRepository contractLedgerRepository;

    private final CustomerAppService customerAppService;

    private final OrderApprovalPort orderApprovalPort;

    private final ErpOrderPort erpOrderPort;

    private final OrderPushLogRepository orderPushLogRepository;

    private final CrmProperties properties;

    private final CrmOrderApprovalProperties approvalProperties;

    private final CrmErpProperties erpProperties;

    @Override
    public PageResult<SalesOrderView> page(SalesOrderQuery query, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
        List<SalesOrder> orders = salesOrderRepository.page(query);
        long total = salesOrderRepository.count(query);
        return PageResult.of(orders.stream().map(SalesOrderView::from).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public SalesOrderView detail(Long id, CrmActor actor) {
        assertGate();
        SalesOrder order = requireOrder(id, actor);
        return view(order, salesOrderRepository.findLines(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView create(SalesOrderCreateCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        Source source = resolveSource(normalizeSourceType(command.getSourceType()), command, actor);
        CustomerView customer = customerAppService.detail(source.customerId, actor).getCustomer();
        SalesOrder order = new SalesOrder();
        order.setOrderNo(nextOrderNo());
        order.setSourceType(source.type);
        order.setSourceId(command.getSourceId());
        order.setSourceOrderNo(source.sourceOrderNo);
        order.setCustomerId(source.customerId);
        order.setCustomerCode(customer.getCustomerCode());
        order.setTitle(source.title);
        order.setCurrency(StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY);
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setOwnerId(customer.getOwnerId() == null ? userId : customer.getOwnerId());
        order.setDeptId(customer.getDeptId());
        order.setRemark(command.getRemark());
        order.setLines(source.lines);
        requireLines(order.getLines());
        salesOrderRepository.insert(order);
        if (source.quote != null) {
            source.quote.markConverted();
            quoteRepository.update(source.quote);
        }
        return view(order, order.getLines());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView update(Long id, SalesOrderUpdateCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        SalesOrder order = requireOrder(id, actor);
        order.requireEditable();
        List<SalesOrderLine> lines = mapUpdateLines(command.getLines());
        requireLines(lines);
        order.setTitle(StringUtils.hasText(command.getTitle()) ? command.getTitle() : order.getTitle());
        order.setCurrency(StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : order.getCurrency());
        order.setRemark(command.getRemark());
        order.setLines(lines);
        salesOrderRepository.replaceLines(order);
        salesOrderRepository.update(order);
        return view(order, lines);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView submit(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        SalesOrder order = requireOrder(id, actor);
        order.setLines(salesOrderRepository.findLines(id));
        order.validateForSubmit();
        if (!approvalProperties.isEnabled() || approvalProperties.getDefinitionId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_APPROVAL_UNAVAILABLE);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("orderNo", order.getOrderNo());
        variables.put("customerId", order.getCustomerId());
        variables.put("totalAmount", order.getTotalAmount());
        Long instanceId = orderApprovalPort.start(approvalProperties.getDefinitionId(),
                "商务订单审批：" + order.getOrderNo(), "crm-order:" + order.getId(), variables, actor);
        order.submit(instanceId, LocalDateTime.now());
        salesOrderRepository.replaceLines(order);
        salesOrderRepository.update(order);
        return view(order, order.getLines());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView cancel(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        SalesOrder order = requireOrder(id, actor);
        cancelErpOrderIfNeeded(order);
        order.cancel();
        salesOrderRepository.update(order);
        orderOutboxRepository.cancelByOrderId(id);
        return view(order, salesOrderRepository.findLines(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderView retry(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        SalesOrder order = requireOrder(id, actor);
        if (order.getStatus() != SalesOrderStatus.APPROVED && order.getStatus() != SalesOrderStatus.PUSH_FAILED) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "仅审批通过或下发失败订单允许重试");
        }
        if (!erpProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_PUSH_FAILED, "ERP 下发未启用");
        }
        orderOutboxRepository.resetForRetry(id, LocalDateTime.now());
        return view(order, salesOrderRepository.findLines(id));
    }

    @Override
    public int pushPending() {
        if (!erpProperties.isEnabled()) {
            return 0;
        }
        List<OrderOutboxEvent> events = orderOutboxRepository.findPending(erpProperties.getBatchSize(),
                LocalDateTime.now());
        int pushed = 0;
        for (OrderOutboxEvent event : events) {
            TenantContext.set(event.getTenantId());
            try {
                if (pushOne(event)) {
                    pushed++;
                }
            } catch (Exception exception) {
                log.warn("商务订单下发异常 orderId={}", event.getAggregateId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return pushed;
    }

    @Override
    public int reconcile() {
        if (!erpProperties.isEnabled()) {
            return 0;
        }
        List<SalesOrder> orders = salesOrderRepository.findByStatuses(List.of(
                SalesOrderStatus.PUSHING.name(), SalesOrderStatus.PUSH_FAILED.name(),
                SalesOrderStatus.PUSHED.name()), erpProperties.getBatchSize());
        int refreshed = 0;
        for (SalesOrder order : orders) {
            TenantContext.set(order.getTenantId());
            try {
                if (reconcileOne(order)) {
                    refreshed++;
                }
            } catch (Exception exception) {
                log.warn("商务订单对账失败 orderId={}", order.getId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return refreshed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalCallback(SalesOrderApprovalCallbackCommand command) {
        SalesOrder order = salesOrderRepository.findByApprovalInstanceId(command.getInstanceId());
        if (order == null) {
            return false;
        }
        boolean approved = Boolean.TRUE.equals(command.getApproved());
        LocalDateTime now = LocalDateTime.now();
        if (!salesOrderRepository.applyApprovalResult(order.getId(), approved, now)) {
            return false;
        }
        if (approved) {
            orderOutboxRepository.enqueue(order.getId(), "{\"orderId\":" + order.getId() + "}", now);
        }
        return true;
    }

    /**
     * 下发单条事件。
     *
     * @param event 事件
     * @return 是否下发成功
     */
    private boolean pushOne(OrderOutboxEvent event) {
        SalesOrder order = salesOrderRepository.findById(event.getAggregateId());
        if (order == null) {
            orderOutboxRepository.markSent(event.getId());
            return false;
        }
        if (order.getStatus() == SalesOrderStatus.PUSHED || !isPushableStatus(order.getStatus())) {
            orderOutboxRepository.markSent(event.getId());
            return false;
        }
        order.setLines(salesOrderRepository.findLines(order.getId()));
        long start = System.currentTimeMillis();
        try {
            order.beginPush();
            salesOrderRepository.update(order);
            ErpOrderResult result = erpOrderPort.create(buildErpCommand(order));
            order.markPushed(result.erpOrderNo(), result.erpOrderId(), result.status(), LocalDateTime.now());
            salesOrderRepository.update(order);
            orderOutboxRepository.markSent(event.getId());
            logPushResult(order, null, result.erpOrderNo(), System.currentTimeMillis() - start);
            return true;
        } catch (Exception exception) {
            String message = messageOf(exception);
            order.markPushFailed(message);
            salesOrderRepository.update(order);
            orderOutboxRepository.markFailed(event.getId(), message, nextRetryAt(order.getPushAttempts()));
            logPushResult(order, message, null, System.currentTimeMillis() - start);
            return false;
        }
    }

    /**
     * 对账单条订单。
     *
     * @param order 订单
     * @return 是否刷新
     */
    private boolean reconcileOne(SalesOrder order) {
        ErpOrderResult result = erpOrderPort.findByCrmOrderNo(order.getOrderNo());
        if (result == null) {
            if (order.getStatus() == SalesOrderStatus.PUSH_FAILED
                    || order.getStatus() == SalesOrderStatus.PUSHING) {
                orderOutboxRepository.resetForRetry(order.getId(), LocalDateTime.now());
            }
            return false;
        }
        if (order.getStatus() == SalesOrderStatus.PUSHED) {
            order.refreshErpStatus(result.status(), result.erpOrderNo(), result.erpOrderId());
        } else {
            order.adoptErpOrder(result.erpOrderNo(), result.erpOrderId(), result.status(), LocalDateTime.now());
            orderOutboxRepository.cancelByOrderId(order.getId());
        }
        salesOrderRepository.update(order);
        return true;
    }

    /**
     * 取消 ERP 原单（已下发订单取消前调用）。
     *
     * @param order 订单
     */
    private void cancelErpOrderIfNeeded(SalesOrder order) {
        if (order.getStatus() != SalesOrderStatus.PUSHED || !StringUtils.hasText(order.getErpOrderNo())) {
            return;
        }
        if (!erpProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_PUSH_FAILED, "ERP 下发未启用，无法取消已下发订单");
        }
        try {
            erpOrderPort.cancel(order.getErpOrderNo());
        } catch (BizException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT,
                    "ERP 拒绝取消：" + exception.getMessage());
        }
    }

    /**
     * 解析订单来源。
     *
     * @param type 来源类型
     * @param command 创建命令
     * @param actor 当前操作人
     * @return 来源上下文
     */
    private Source resolveSource(String type, SalesOrderCreateCommand command, CrmActor actor) {
        Source source = new Source();
        source.type = type;
        if (SOURCE_QUOTE.equals(type)) {
            resolveQuoteSource(source, command, actor);
        } else if (SOURCE_CONTRACT.equals(type)) {
            resolveContractSource(source, command, actor);
        } else {
            if (command.getCustomerId() == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "手工订单必须指定客户");
            }
            source.customerId = command.getCustomerId();
            source.title = command.getTitle();
            source.lines = mapCreateLines(command.getLines());
        }
        return source;
    }

    /**
     * 解析报价来源。
     *
     * @param source 来源上下文
     * @param command 创建命令
     * @param actor 当前操作人
     */
    private void resolveQuoteSource(Source source, SalesOrderCreateCommand command, CrmActor actor) {
        if (command.getSourceId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "报价转订单必须指定报价ID");
        }
        Quote quote = quoteRepository.findById(command.getSourceId());
        if (quote == null) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_NOT_FOUND);
        }
        customerAppService.detail(quote.getCustomerId(), actor);
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "仅审批通过的报价允许转商务订单");
        }
        source.quote = quote;
        source.customerId = quote.getCustomerId();
        source.title = StringUtils.hasText(command.getTitle()) ? command.getTitle() : quote.getTitle();
        source.sourceOrderNo = quote.getQuoteNo();
        if (command.getLines() == null || command.getLines().isEmpty()) {
            source.lines = copyQuoteLines(quoteRepository.findLines(quote.getId()));
        } else {
            source.lines = mapCreateLines(command.getLines());
        }
    }

    /**
     * 解析合同来源。
     *
     * @param source 来源上下文
     * @param command 创建命令
     * @param actor 当前操作人
     */
    private void resolveContractSource(Source source, SalesOrderCreateCommand command, CrmActor actor) {
        if (command.getSourceId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "合同转订单必须指定合同台账ID");
        }
        ContractLedger ledger = contractLedgerRepository.findById(command.getSourceId());
        if (ledger == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        customerAppService.detail(ledger.getCustomerId(), actor);
        source.customerId = ledger.getCustomerId();
        source.title = StringUtils.hasText(command.getTitle()) ? command.getTitle() : ledger.getTitle();
        source.sourceOrderNo = ledger.getContractNo();
        source.lines = mapCreateLines(command.getLines());
    }

    /**
     * 复制报价行（保留金额，折算单价；物料编码留空待补录）。
     *
     * @param quoteLines 报价行
     * @return 订单行
     */
    private List<SalesOrderLine> copyQuoteLines(List<QuoteLine> quoteLines) {
        List<SalesOrderLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (QuoteLine quoteLine : quoteLines) {
            SalesOrderLine line = new SalesOrderLine();
            line.setLineNo(lineNo++);
            line.setItemName(quoteLine.getProductName());
            line.setSpecification(quoteLine.getSpecification());
            line.setUom(quoteLine.getUnit());
            line.setQuantity(quoteLine.getQuantity());
            line.setUnitPrice(effectivePrice(quoteLine));
            line.setAmount(quoteLine.getLineAmount());
            line.setRemark(quoteLine.getRemark());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 折算报价行有效单价（行金额 / 数量）。
     *
     * @param quoteLine 报价行
     * @return 单价
     */
    private BigDecimal effectivePrice(QuoteLine quoteLine) {
        BigDecimal quantity = quoteLine.getQuantity();
        BigDecimal amount = quoteLine.getLineAmount();
        if (quantity != null && quantity.signum() > 0 && amount != null) {
            return amount.divide(quantity, AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return quoteLine.getUnitPrice();
    }

    /**
     * 创建命令行转换。
     *
     * @param lines 命令行
     * @return 订单行
     */
    private List<SalesOrderLine> mapCreateLines(List<SalesOrderCreateCommand.Line> lines) {
        List<SalesOrderLine> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        int lineNo = 1;
        for (SalesOrderCreateCommand.Line source : lines) {
            result.add(buildLine(lineNo++, source.getItemCode(), source.getItemName(), source.getSpecification(),
                    source.getUom(), source.getQuantity(), source.getUnitPrice(), source.getRemark()));
        }
        return result;
    }

    /**
     * 编辑命令行转换。
     *
     * @param lines 命令行
     * @return 订单行
     */
    private List<SalesOrderLine> mapUpdateLines(List<SalesOrderUpdateCommand.Line> lines) {
        List<SalesOrderLine> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        int lineNo = 1;
        for (SalesOrderUpdateCommand.Line source : lines) {
            result.add(buildLine(lineNo++, source.getItemCode(), source.getItemName(), source.getSpecification(),
                    source.getUom(), source.getQuantity(), source.getUnitPrice(), source.getRemark()));
        }
        return result;
    }

    /**
     * 构建订单行。
     *
     * @param lineNo 行号
     * @param itemCode 物料编码
     * @param itemName 物料名称
     * @param specification 规格
     * @param uom 单位
     * @param quantity 数量
     * @param unitPrice 单价
     * @param remark 备注
     * @return 订单行
     */
    private SalesOrderLine buildLine(int lineNo, String itemCode, String itemName, String specification,
                                     String uom, BigDecimal quantity, BigDecimal unitPrice, String remark) {
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(lineNo);
        line.setItemCode(StringUtils.hasText(itemCode) ? itemCode.trim() : null);
        line.setItemName(itemName);
        line.setSpecification(specification);
        line.setUom(uom);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setAmount(line.calculateAmount());
        line.setRemark(remark);
        return line;
    }

    /**
     * 构建 ERP 下发命令。
     *
     * @param order 订单
     * @return ERP 命令
     */
    private ErpOrderCommand buildErpCommand(SalesOrder order) {
        ErpOrderCommand command = new ErpOrderCommand();
        command.setCrmOrderNo(order.getOrderNo());
        command.setCrmCustomerCode(order.getCustomerCode());
        command.setDocDate(LocalDate.now());
        command.setCurrency(order.getCurrency());
        command.setRemark(order.getRemark());
        for (SalesOrderLine line : order.getLines()) {
            ErpOrderCommand.Line erpLine = new ErpOrderCommand.Line();
            erpLine.setItemCode(line.getItemCode());
            erpLine.setItemName(line.getItemName());
            erpLine.setQuantity(line.getQuantity());
            erpLine.setUnitPrice(line.getUnitPrice());
            erpLine.setUom(line.getUom());
            command.getLines().add(erpLine);
        }
        return command;
    }

    /**
     * 记录下发日志。
     *
     * @param order 订单
     * @param error 失败原因（成功传 null）
     * @param responseRef ERP 单号
     * @param costMs 耗时
     */
    private void logPushResult(SalesOrder order, String error, String responseRef, long costMs) {
        orderPushLogRepository.record(order.getId(), order.getPushAttempts(), order.getOrderNo(),
                responseRef, error == null ? "SUCCESS" : "FAILED", error, costMs);
    }

    /**
     * 计算下次重试时间（指数退避；超过最大次数则静默）。
     *
     * @param attempts 尝试次数
     * @return 下次重试时间
     */
    private LocalDateTime nextRetryAt(Integer attempts) {
        int attempt = attempts == null ? 1 : attempts;
        if (attempt >= erpProperties.getMaxAttempts()) {
            return LocalDateTime.now().plusDays(STOP_RETRY_DAYS);
        }
        int exponent = Math.min(attempt - 1, MAX_BACKOFF_EXPONENT);
        long minutes = (long) erpProperties.getBackoffBaseMinutes() * (1L << exponent);
        long capped = Math.min(minutes, erpProperties.getBackoffMaxMinutes());
        return LocalDateTime.now().plusMinutes(Math.max(1L, capped));
    }

    /**
     * 校验订单行非空。
     *
     * @param lines 订单行
     */
    private void requireLines(List<SalesOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID, "商务订单至少需要一行明细");
        }
    }

    /**
     * 判断状态是否允许下发。
     *
     * @param status 状态
     * @return 允许下发返回 true
     */
    private boolean isPushableStatus(SalesOrderStatus status) {
        return status == SalesOrderStatus.APPROVED || status == SalesOrderStatus.PUSH_FAILED
                || status == SalesOrderStatus.PUSHING;
    }

    /**
     * 生成下一个订单编号。
     *
     * @return 订单编号
     */
    private String nextOrderNo() {
        String prefix = ORDER_NO_PREFIX + LocalDate.now().format(ORDER_DATE_FORMAT) + "-";
        long sequence = salesOrderRepository.maxSequence(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    /**
     * 规范化来源类型。
     *
     * @param value 原始值
     * @return 来源类型
     */
    private String normalizeSourceType(String value) {
        String type = value == null ? SOURCE_MANUAL : value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_MANUAL.equals(type) && !SOURCE_QUOTE.equals(type) && !SOURCE_CONTRACT.equals(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "来源类型只允许 MANUAL / QUOTE / CONTRACT");
        }
        return type;
    }

    /**
     * 查询并校验订单数据范围。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单
     */
    private SalesOrder requireOrder(Long id, CrmActor actor) {
        SalesOrder order = salesOrderRepository.findById(id);
        if (order == null || (!actor.allDataScope() && !Objects.equals(order.getOwnerId(), actor.requireUserId()))) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 组装订单视图。
     *
     * @param order 订单
     * @param lines 订单行
     * @return 视图
     */
    private SalesOrderView view(SalesOrder order, List<SalesOrderLine> lines) {
        order.setLines(lines);
        return SalesOrderView.from(order);
    }

    /**
     * 提取异常消息。
     *
     * @param exception 异常
     * @return 消息
     */
    private String messageOf(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
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
     * 订单来源上下文。
     *
     * @author BlueWhale
     */
    private static class Source {

        /** 来源类型 */
        private String type;

        /** 客户 ID */
        private Long customerId;

        /** 标题 */
        private String title;

        /** 来源单据编号 */
        private String sourceOrderNo;

        /** 订单行 */
        private List<SalesOrderLine> lines = new ArrayList<>();

        /** 来源报价（用于转单标记） */
        private Quote quote;
    }
}
