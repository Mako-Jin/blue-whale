package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.InvoiceApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceIssueCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceRedFlushCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSaveCommand;
import com.bluewhale.crm.commerce.application.command.InvoiceSendCommand;
import com.bluewhale.crm.commerce.application.service.InvoiceAppService;
import com.bluewhale.crm.commerce.application.view.InvoiceView;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.InvoiceItem;
import com.bluewhale.crm.commerce.domain.model.InvoiceQuery;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequestType;
import com.bluewhale.crm.commerce.domain.model.InvoiceStatus;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderStatus;
import com.bluewhale.crm.commerce.domain.port.InvoiceApprovalPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.commerce.domain.repository.InvoiceRequestRepository;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmInvoiceApprovalProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 开票申请应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class InvoiceAppServiceImpl implements InvoiceAppService {

    /** 来源类型：合同台账 */
    private static final String SOURCE_CONTRACT = "CONTRACT";

    /** 来源类型：商务订单 */
    private static final String SOURCE_ORDER = "ORDER";

    /** 申请单号前缀 */
    private static final String INVOICE_NO_PREFIX = "INV-";

    /** 申请单号日期格式 */
    private static final DateTimeFormatter INVOICE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 默认发票类型 */
    private static final String DEFAULT_INVOICE_TYPE = "VAT_SPECIAL";

    /** 默认抬头类型 */
    private static final String DEFAULT_TITLE_TYPE = "COMPANY";

    private final InvoiceRequestRepository invoiceRequestRepository;

    private final ContractLedgerRepository contractLedgerRepository;

    private final SalesOrderRepository salesOrderRepository;

    private final CustomerAppService customerAppService;

    private final InvoiceApprovalPort invoiceApprovalPort;

    private final CrmProperties properties;

    private final CrmInvoiceApprovalProperties approvalProperties;

    @Override
    public PageResult<InvoiceView> page(InvoiceQuery query, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
        List<InvoiceRequest> requests = invoiceRequestRepository.page(query);
        long total = invoiceRequestRepository.count(query);
        return PageResult.of(requests.stream().map(InvoiceView::from).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public InvoiceView detail(Long id, CrmActor actor) {
        assertGate();
        InvoiceRequest request = requireRequest(id, actor);
        request.setItems(invoiceRequestRepository.findItems(id));
        InvoiceView view = InvoiceView.from(request);
        view.setAvailableAmount(availableAmount(request.getSourceType(), request.getSourceId()));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView create(InvoiceSaveCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        Source source = resolveSource(normalizeSourceType(command.getSourceType()), command.getSourceId(), actor);
        BigDecimal amount = command.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "开票申请金额必须大于 0");
        }
        BigDecimal available = availableAmount(source.type, source.sourceId);
        if (amount.compareTo(available) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_AMOUNT_EXCEEDED,
                    "申请金额 " + amount + " 超过可开票余额 " + available);
        }
        InvoiceRequest request = new InvoiceRequest();
        request.setInvoiceNo(nextInvoiceNo());
        request.setRequestType(InvoiceRequestType.NORMAL);
        request.setSourceType(source.type);
        request.setSourceId(source.sourceId);
        request.setSourceOrderNo(source.sourceOrderNo);
        request.setContractId(source.contractId);
        request.setContractNo(source.contractNo);
        request.setCustomerId(source.customer.getId());
        request.setCustomerCode(source.customer.getCustomerCode());
        request.setCustomerName(source.customer.getName());
        request.setCurrency(currencyOf(command.getCurrency(), source.currency));
        request.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        request.setTaxAmount(BigDecimal.ZERO);
        request.setInvoiceType(invoiceTypeOf(command.getInvoiceType()));
        request.setTitleType(titleTypeOf(command.getTitleType()));
        request.setInvoiceTitle(StringUtils.hasText(command.getInvoiceTitle())
                ? command.getInvoiceTitle().trim() : source.customer.getName());
        request.setTaxNo(command.getTaxNo());
        request.setStatus(InvoiceStatus.DRAFT);
        request.setOwnerId(source.customer.getOwnerId() == null ? userId : source.customer.getOwnerId());
        request.setDeptId(source.customer.getDeptId());
        request.setRemark(command.getRemark());
        request.setItems(mapItems(command.getItems()));
        request.validateItemTotal();
        invoiceRequestRepository.insert(request);
        return view(request, request.getItems(), available.subtract(request.getAmount()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView update(Long id, InvoiceSaveCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        InvoiceRequest request = requireRequest(id, actor);
        request.requireEditable();
        BigDecimal amount = command.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "开票申请金额必须大于 0");
        }
        BigDecimal available = availableAmount(request.getSourceType(), request.getSourceId());
        if (amount.compareTo(available) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_AMOUNT_EXCEEDED,
                    "申请金额 " + amount + " 超过可开票余额 " + available);
        }
        request.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        request.setInvoiceType(invoiceTypeOf(command.getInvoiceType()));
        request.setTitleType(titleTypeOf(command.getTitleType()));
        if (StringUtils.hasText(command.getInvoiceTitle())) {
            request.setInvoiceTitle(command.getInvoiceTitle().trim());
        }
        request.setTaxNo(command.getTaxNo());
        request.setRemark(command.getRemark());
        request.setCurrency(currencyOf(command.getCurrency(), request.getCurrency()));
        request.setItems(mapItems(command.getItems()));
        request.validateItemTotal();
        invoiceRequestRepository.replaceItems(request);
        invoiceRequestRepository.update(request);
        return view(request, request.getItems(), available.subtract(request.getAmount()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView submit(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        InvoiceRequest request = requireRequest(id, actor);
        request.setItems(invoiceRequestRepository.findItems(id));
        request.validateForSubmit();
        if (!approvalProperties.isEnabled() || approvalProperties.getDefinitionId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_APPROVAL_UNAVAILABLE);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("invoiceNo", request.getInvoiceNo());
        variables.put("customerId", request.getCustomerId());
        variables.put("contractNo", request.getContractNo());
        variables.put("amount", request.getAmount());
        Long instanceId = invoiceApprovalPort.start(approvalProperties.getDefinitionId(),
                "开票申请审批：" + request.getInvoiceNo(), "crm-invoice:" + request.getId(), variables, actor);
        request.submit(instanceId, LocalDateTime.now());
        invoiceRequestRepository.update(request);
        return view(request, request.getItems(),
                availableAmount(request.getSourceType(), request.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView issue(Long id, InvoiceIssueCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        InvoiceRequest request = requireRequest(id, actor);
        if (!StringUtils.hasText(command.getInvoiceNumber())) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "发票号码不能为空");
        }
        request.issue(command.getInvoiceCode(), command.getInvoiceNumber().trim(), command.getIssuedAmount(),
                command.getTaxAmount(), LocalDateTime.now());
        invoiceRequestRepository.update(request);
        request.setItems(invoiceRequestRepository.findItems(id));
        return view(request, request.getItems(),
                availableAmount(request.getSourceType(), request.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView send(Long id, InvoiceSendCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        InvoiceRequest request = requireRequest(id, actor);
        request.send(command.getCourier(), command.getTrackingNo(), LocalDateTime.now());
        invoiceRequestRepository.update(request);
        request.setItems(invoiceRequestRepository.findItems(id));
        return view(request, request.getItems(),
                availableAmount(request.getSourceType(), request.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView redFlush(Long id, InvoiceRedFlushCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        InvoiceRequest origin = requireRequest(id, actor);
        origin.requireRedFlushable();
        BigDecimal amount = command.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "红冲金额必须大于 0");
        }
        BigDecimal issued = origin.getIssuedAmount() == null ? origin.getAmount() : origin.getIssuedAmount();
        BigDecimal flushed = invoiceRequestRepository.sumRedFlushed(origin.getId());
        if (amount.compareTo(issued.subtract(flushed)) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_AMOUNT_EXCEEDED,
                    "红冲金额超过原发票可红冲余额 " + issued.subtract(flushed));
        }
        InvoiceRequest redFlush = new InvoiceRequest();
        redFlush.setInvoiceNo(nextInvoiceNo());
        redFlush.setRequestType(InvoiceRequestType.RED_FLUSH);
        redFlush.setOriginInvoiceId(origin.getId());
        redFlush.setSourceType(origin.getSourceType());
        redFlush.setSourceId(origin.getSourceId());
        redFlush.setSourceOrderNo(origin.getSourceOrderNo());
        redFlush.setContractId(origin.getContractId());
        redFlush.setContractNo(origin.getContractNo());
        redFlush.setCustomerId(origin.getCustomerId());
        redFlush.setCustomerCode(origin.getCustomerCode());
        redFlush.setCustomerName(origin.getCustomerName());
        redFlush.setCurrency(origin.getCurrency());
        redFlush.setAmount(amount.negate().setScale(2, java.math.RoundingMode.HALF_UP));
        redFlush.setTaxAmount(BigDecimal.ZERO);
        redFlush.setInvoiceType(origin.getInvoiceType());
        redFlush.setTitleType(origin.getTitleType());
        redFlush.setInvoiceTitle(origin.getInvoiceTitle());
        redFlush.setTaxNo(origin.getTaxNo());
        redFlush.setOwnerId(origin.getOwnerId() == null ? userId : origin.getOwnerId());
        redFlush.setDeptId(origin.getDeptId());
        redFlush.setRemark(command.getReason());
        redFlush.setIssuedAmount(redFlush.getAmount());
        redFlush.markRedFlushed(command.getReason(), LocalDateTime.now());
        invoiceRequestRepository.insert(redFlush);
        // 原单必须留下被红冲的痕迹：否则列表/对账里它仍显示为「已开票」，看不出已作废，
        // 也无法阻止对同一张票重复发起红冲。部分红冲只保留 ISSUED（余额仍可继续冲）。
        if (flushed.add(amount).compareTo(issued) >= 0) {
            origin.markOriginFullyRedFlushed(command.getReason(), LocalDateTime.now());
            invoiceRequestRepository.update(origin);
        }
        return view(redFlush, redFlush.getItems(),
                availableAmount(redFlush.getSourceType(), redFlush.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView cancel(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        InvoiceRequest request = requireRequest(id, actor);
        request.cancel();
        invoiceRequestRepository.update(request);
        request.setItems(invoiceRequestRepository.findItems(id));
        return view(request, request.getItems(),
                availableAmount(request.getSourceType(), request.getSourceId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalCallback(InvoiceApprovalCallbackCommand command) {
        InvoiceRequest request = invoiceRequestRepository.findByApprovalInstanceId(command.getInstanceId());
        if (request == null) {
            return false;
        }
        boolean approved = Boolean.TRUE.equals(command.getApproved());
        return invoiceRequestRepository.applyApprovalResult(request.getId(), approved, LocalDateTime.now());
    }

    @Override
    public BigDecimal availableAmount(String sourceType, Long sourceId) {
        if (!StringUtils.hasText(sourceType) || sourceId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (SOURCE_CONTRACT.equals(sourceType)) {
            ContractLedger ledger = contractLedgerRepository.findById(sourceId);
            if (ledger != null && ledger.getAmount() != null) {
                total = ledger.getAmount();
            }
        } else if (SOURCE_ORDER.equals(sourceType)) {
            SalesOrder order = salesOrderRepository.findById(sourceId);
            if (order != null && order.getTotalAmount() != null) {
                total = order.getTotalAmount();
            }
        }
        BigDecimal occupied = invoiceRequestRepository.sumOccupiedAmount(sourceType, sourceId);
        return total.subtract(occupied);
    }

    /**
     * 解析开票来源。
     *
     * @param type 来源类型
     * @param sourceId 来源单据 ID
     * @param actor 当前操作人
     * @return 来源上下文
     */
    private Source resolveSource(String type, Long sourceId, CrmActor actor) {
        if (sourceId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "请选择合同或商务订单");
        }
        Source source = new Source();
        source.type = type;
        if (SOURCE_CONTRACT.equals(type)) {
            ContractLedger ledger = contractLedgerRepository.findById(sourceId);
            if (ledger == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "合同台账不存在");
            }
            source.sourceId = ledger.getId();
            source.sourceOrderNo = ledger.getContractNo();
            source.contractId = ledger.getId();
            source.contractNo = ledger.getContractNo();
            source.currency = ledger.getCurrency();
            source.customer = customerAppService.detail(ledger.getCustomerId(), actor).getCustomer();
            return source;
        }
        if (SOURCE_ORDER.equals(type)) {
            SalesOrder order = salesOrderRepository.findById(sourceId);
            if (order == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "商务订单不存在");
            }
            if (!isInvoiceableOrderStatus(order.getStatus())) {
                throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT,
                        "仅审批通过或已下发的商务订单允许申请开票");
            }
            source.sourceId = order.getId();
            source.sourceOrderNo = order.getOrderNo();
            source.currency = order.getCurrency();
            source.customer = customerAppService.detail(order.getCustomerId(), actor).getCustomer();
            return source;
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "来源类型只允许 CONTRACT / ORDER");
    }

    /**
     * 判断订单状态是否允许申请开票。
     *
     * @param status 订单状态
     * @return 允许返回 true
     */
    private boolean isInvoiceableOrderStatus(SalesOrderStatus status) {
        return status == SalesOrderStatus.APPROVED || status == SalesOrderStatus.PUSHING
                || status == SalesOrderStatus.PUSHED;
    }

    /**
     * 映射申请行。
     *
     * @param items 行命令
     * @return 申请行
     */
    private List<InvoiceItem> mapItems(List<InvoiceSaveCommand.Item> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(source -> {
            InvoiceItem item = new InvoiceItem();
            item.setItemName(source.getItemName());
            item.setSpecification(source.getSpecification());
            item.setQuantity(source.getQuantity());
            item.setUnitPrice(source.getUnitPrice());
            item.setTaxRate(source.getTaxRate());
            item.setTaxAmount(source.getTaxAmount());
            item.setRemark(source.getRemark());
            item.setAmount(item.calculateAmount());
            return item;
        }).toList();
    }

    /**
     * 生成下一个开票申请单号。
     *
     * @return 单号
     */
    private String nextInvoiceNo() {
        String prefix = INVOICE_NO_PREFIX + LocalDate.now().format(INVOICE_DATE_FORMAT) + "-";
        long sequence = invoiceRequestRepository.maxSequence(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    /**
     * 查询并校验开票申请数据范围。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请
     */
    private InvoiceRequest requireRequest(Long id, CrmActor actor) {
        InvoiceRequest request = invoiceRequestRepository.findById(id);
        if (request == null
                || (!actor.allDataScope() && !Objects.equals(request.getOwnerId(), actor.requireUserId()))) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_NOT_FOUND);
        }
        return request;
    }

    /**
     * 组装视图。
     *
     * @param request 申请
     * @param items 申请行
     * @param available 可开票余额
     * @return 视图
     */
    private InvoiceView view(InvoiceRequest request, List<InvoiceItem> items, BigDecimal available) {
        request.setItems(items);
        InvoiceView view = InvoiceView.from(request);
        view.setAvailableAmount(available);
        return view;
    }

    /**
     * 规范化来源类型。
     *
     * @param value 原始值
     * @return 来源类型
     */
    private String normalizeSourceType(String value) {
        String type = value == null ? SOURCE_CONTRACT : value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_CONTRACT.equals(type) && !SOURCE_ORDER.equals(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "来源类型只允许 CONTRACT / ORDER");
        }
        return type;
    }

    /**
     * 规范化发票类型。
     *
     * @param value 原始值
     * @return 发票类型
     */
    private String invoiceTypeOf(String value) {
        String type = value == null ? DEFAULT_INVOICE_TYPE : value.trim().toUpperCase(Locale.ROOT);
        if (!"VAT_SPECIAL".equals(type) && !"VAT_NORMAL".equals(type) && !"ELECTRONIC".equals(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "发票类型只允许 VAT_SPECIAL / VAT_NORMAL / ELECTRONIC");
        }
        return type;
    }

    /**
     * 规范化抬头类型。
     *
     * @param value 原始值
     * @return 抬头类型
     */
    private String titleTypeOf(String value) {
        String type = value == null ? DEFAULT_TITLE_TYPE : value.trim().toUpperCase(Locale.ROOT);
        if (!"COMPANY".equals(type) && !"PERSONAL".equals(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "抬头类型只允许 COMPANY / PERSONAL");
        }
        return type;
    }

    /**
     * 规范化币种。
     *
     * @param value 原始值
     * @param fallback 兜底值
     * @return 币种
     */
    private String currencyOf(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim().toUpperCase(Locale.ROOT);
        }
        return StringUtils.hasText(fallback) ? fallback : DEFAULT_CURRENCY;
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
     * 开票来源上下文。
     *
     * @author BlueWhale
     */
    private static class Source {

        /** 来源类型 */
        private String type;

        /** 来源单据 ID */
        private Long sourceId;

        /** 来源单据编号 */
        private String sourceOrderNo;

        /** 合同台账 ID */
        private Long contractId;

        /** 合同编号 */
        private String contractNo;

        /** 币种 */
        private String currency;

        /** 客户 */
        private CustomerView customer;
    }
}
