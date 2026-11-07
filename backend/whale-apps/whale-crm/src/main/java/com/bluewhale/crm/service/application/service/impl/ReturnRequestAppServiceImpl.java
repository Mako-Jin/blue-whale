package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmReturnApprovalProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.service.application.command.ReturnApprovalCallbackCommand;
import com.bluewhale.crm.service.application.command.ReturnRequestCommand;
import com.bluewhale.crm.service.application.command.ReturnRequestLineCommand;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import com.bluewhale.crm.service.application.view.ReturnRequestView;
import com.bluewhale.crm.service.domain.model.ReturnOutboxEvent;
import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnRequestItem;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;
import com.bluewhale.crm.service.domain.model.ReturnStatus;
import com.bluewhale.crm.service.domain.port.ErpReturnCommand;
import com.bluewhale.crm.service.domain.port.ErpReturnPort;
import com.bluewhale.crm.service.domain.port.ErpReturnResult;
import com.bluewhale.crm.service.domain.port.ReturnApprovalPort;
import com.bluewhale.crm.service.domain.repository.ReturnOutboxRepository;
import com.bluewhale.crm.service.domain.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 退换货应用服务实现。
 *
 * <p>审批通过后在同一事务写入 outbox，由定时任务幂等下发 ERP 退货过账；失败按指数退避重试，
 * 并按 CRM 单号对账回读 ERP 退货单号与状态。库存、应收与凭证均由 ERP 过账完成，CRM 不写库存。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class ReturnRequestAppServiceImpl implements ReturnRequestAppService {

    /** 申请类型：退货 */
    private static final String TYPE_RETURN = "RETURN";

    /** 申请单号前缀 */
    private static final String RETURN_NO_PREFIX = "RET-";

    /** 申请单号日期格式 */
    private static final DateTimeFormatter RETURN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 计入可退量占用的状态 */
    private static final List<String> OCCUPIED_STATUSES = List.of(
            ReturnStatus.APPROVED.name(), ReturnStatus.EXECUTING.name(), ReturnStatus.COMPLETED.name());

    /** 对账扫描状态 */
    private static final List<String> RECONCILE_STATUSES = List.of(
            ReturnStatus.EXECUTING.name(), ReturnStatus.FAILED.name());

    /** 退避最大指数 */
    private static final int MAX_BACKOFF_EXPONENT = 16;

    /** 达到最大尝试次数后的静默间隔（天） */
    private static final long STOP_RETRY_DAYS = 30L;

    /** 失败原因长度上限 */
    private static final int ERROR_MAX_LENGTH = 1000;

    private final ReturnRequestRepository returnRequestRepository;

    private final ReturnOutboxRepository returnOutboxRepository;

    private final SalesOrderRepository salesOrderRepository;

    private final CustomerAppService customerAppService;

    private final ErpReturnPort erpReturnPort;

    private final ReturnApprovalPort returnApprovalPort;

    private final CrmProperties properties;

    private final CrmServiceProperties serviceProperties;

    private final CrmReturnApprovalProperties approvalProperties;

    private final CrmErpProperties erpProperties;

    @Override
    public PageResult<ReturnRequestView> page(ReturnRequestQuery query, CrmActor actor) {
        assertGate();
        ReturnRequestQuery safe = query == null ? new ReturnRequestQuery() : query;
        if (!actor.allDataScope()) {
            safe.setOwnerId(actor.requireUserId());
        }
        List<ReturnRequestView> records = returnRequestRepository.page(safe).stream()
                .map(request -> ReturnRequestView.from(request, returnRequestRepository.findItems(request.getId())))
                .toList();
        return PageResult.of(records, returnRequestRepository.count(safe), safe.getCurrent(), safe.getSize());
    }

    @Override
    public ReturnRequestView detail(Long id, CrmActor actor) {
        assertGate();
        ReturnRequest request = requireRequest(id, actor);
        return view(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView create(ReturnRequestCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        SalesOrder order = requireOrder(command.getOrderId(), actor);
        ReturnRequest request = new ReturnRequest();
        request.setReturnNo(nextReturnNo());
        request.setOrderId(order.getId());
        request.setStatus(ReturnStatus.DRAFT);
        request.setPushAttempts(0);
        request.setLockVersion(0);
        request.setOwnerId(order.getOwnerId() == null ? userId : order.getOwnerId());
        request.setDeptId(order.getDeptId());
        applyHeader(request, command, order, actor);
        request.setItems(mapItems(command.getLines(), order, request.getId()));
        returnRequestRepository.insert(request);
        return ReturnRequestView.from(request, request.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView update(Long id, ReturnRequestCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ReturnRequest request = requireRequest(id, actor);
        request.requireEditable();
        SalesOrder order = requireOrder(command.getOrderId(), actor);
        request.setOrderId(order.getId());
        request.setOwnerId(order.getOwnerId() == null ? userId : order.getOwnerId());
        request.setDeptId(order.getDeptId());
        applyHeader(request, command, order, actor);
        request.setItems(mapItems(command.getLines(), order, request.getId()));
        returnRequestRepository.replaceItems(request);
        returnRequestRepository.update(request);
        return ReturnRequestView.from(request, request.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView submit(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        ReturnRequest request = requireRequest(id, actor);
        request.setItems(returnRequestRepository.findItems(id));
        if (request.getItems().isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "退换货申请至少需要一行明细");
        }
        if (!StringUtils.hasText(request.getErpOrderNo())) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "来源订单尚未下发 ERP，不能提交退货申请");
        }
        if (!approvalProperties.isEnabled() || approvalProperties.getDefinitionId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "退换货审批未启用");
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("returnNo", request.getReturnNo());
        variables.put("customerId", request.getCustomerId());
        variables.put("orderId", request.getOrderId());
        variables.put("totalAmount", totalAmount(request.getItems()));
        Long instanceId = returnApprovalPort.start(approvalProperties.getDefinitionId(),
                "退换货审批：" + request.getReturnNo(), "crm-return:" + request.getId(), variables, actor);
        request.submit(instanceId);
        returnRequestRepository.update(request);
        return ReturnRequestView.from(request, request.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView cancel(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        ReturnRequest request = requireRequest(id, actor);
        request.cancel();
        returnRequestRepository.update(request);
        returnOutboxRepository.cancelByRequestId(id);
        return view(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView retry(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        ReturnRequest request = requireRequest(id, actor);
        if (request.getStatus() != ReturnStatus.FAILED && request.getStatus() != ReturnStatus.APPROVED) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                    "仅审批通过或下发失败的退换货申请允许重试");
        }
        if (!erpProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_ERP_UNAVAILABLE);
        }
        returnOutboxRepository.resetForRetry(id, LocalDateTime.now());
        return view(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestView reconcile(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        ReturnRequest request = requireRequest(id, actor);
        if (!erpProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_ERP_UNAVAILABLE);
        }
        reconcileOne(request);
        return view(returnRequestRepository.findById(id));
    }

    @Override
    public int pushPending() {
        if (!erpProperties.isEnabled()) {
            return 0;
        }
        List<ReturnOutboxEvent> events = returnOutboxRepository.findPending(erpProperties.getBatchSize(),
                LocalDateTime.now());
        int pushed = 0;
        for (ReturnOutboxEvent event : events) {
            TenantContext.set(event.getTenantId());
            try {
                if (pushOne(event)) {
                    pushed++;
                }
            } catch (Exception exception) {
                log.warn("退换货下发异常 returnId={}", event.getAggregateId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return pushed;
    }

    @Override
    public int reconcileAll() {
        if (!erpProperties.isEnabled()) {
            return 0;
        }
        List<ReturnRequest> requests = returnRequestRepository.findByStatuses(RECONCILE_STATUSES,
                erpProperties.getBatchSize());
        int refreshed = 0;
        for (ReturnRequest request : requests) {
            TenantContext.set(request.getTenantId());
            try {
                if (reconcileOne(request)) {
                    refreshed++;
                }
            } catch (Exception exception) {
                log.warn("退换货对账失败 returnId={}", request.getId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return refreshed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleApprovalCallback(ReturnApprovalCallbackCommand command) {
        ReturnRequest request = returnRequestRepository.findByApprovalInstanceId(command.getInstanceId());
        if (request == null) {
            return false;
        }
        boolean approved = Boolean.TRUE.equals(command.getApproved());
        if (!returnRequestRepository.applyApprovalResult(request.getId(), approved, LocalDateTime.now())) {
            return false;
        }
        if (approved) {
            returnOutboxRepository.enqueue(request.getId(), "{\"returnId\":" + request.getId() + "}",
                    LocalDateTime.now());
        }
        return true;
    }

    /**
     * 下发单条事件。
     *
     * @param event 事件
     * @return 是否下发成功
     */
    private boolean pushOne(ReturnOutboxEvent event) {
        ReturnRequest request = returnRequestRepository.findById(event.getAggregateId());
        if (request == null) {
            returnOutboxRepository.markSent(event.getId());
            return false;
        }
        if (request.getStatus() == ReturnStatus.COMPLETED || !request.isExecutable()) {
            returnOutboxRepository.markSent(event.getId());
            return false;
        }
        request.setItems(returnRequestRepository.findItems(request.getId()));
        try {
            request.beginExecute();
            returnRequestRepository.update(request);
            ErpReturnResult result = erpReturnPort.create(buildErpCommand(request));
            request.markCompleted(result.erpReturnNo(), result.status());
            returnRequestRepository.update(request);
            returnOutboxRepository.markSent(event.getId());
            return true;
        } catch (Exception exception) {
            String message = truncate(messageOf(exception));
            request.markFailed(message);
            returnRequestRepository.update(request);
            returnOutboxRepository.markFailed(event.getId(), message, nextRetryAt(request.getPushAttempts()));
            return false;
        }
    }

    /**
     * 对账单张申请。
     *
     * @param request 申请
     * @return 是否刷新
     */
    private boolean reconcileOne(ReturnRequest request) {
        ErpReturnResult result = erpReturnPort.findByCrmReturnNo(request.getReturnNo());
        if (result == null) {
            if (request.getStatus() == ReturnStatus.EXECUTING || request.getStatus() == ReturnStatus.FAILED) {
                returnOutboxRepository.resetForRetry(request.getId(), LocalDateTime.now());
            }
            return false;
        }
        if (request.getStatus() == ReturnStatus.COMPLETED) {
            request.refreshErpStatus(result.status(), result.erpReturnNo());
        } else {
            request.markCompleted(result.erpReturnNo(), result.status());
            returnOutboxRepository.cancelByRequestId(request.getId());
        }
        returnRequestRepository.update(request);
        return true;
    }

    /**
     * 应用申请头字段（来源订单快照、类型、原因）。
     *
     * @param request 申请
     * @param command 命令
     * @param order 来源订单
     * @param actor 当前操作人
     */
    private void applyHeader(ReturnRequest request, ReturnRequestCommand command, SalesOrder order, CrmActor actor) {
        CustomerView customer = customerAppService.detail(order.getCustomerId(), actor).getCustomer();
        request.setRequestType(normalizeType(command.getRequestType()));
        request.setErpOrderNo(order.getErpOrderNo());
        request.setCustomerId(order.getCustomerId());
        request.setCustomerCode(customer.getCustomerCode());
        request.setCustomerName(customer.getName());
        request.setReason(command.getReason());
    }

    /**
     * 规范化申请类型；本期仅开放退货。
     *
     * @param value 原始值
     * @return 类型
     */
    private String normalizeType(String value) {
        String type = StringUtils.hasText(value) ? value.trim().toUpperCase() : TYPE_RETURN;
        if (!TYPE_RETURN.equals(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "本期仅支持退货申请，换货暂未开放");
        }
        return type;
    }

    /**
     * 构建申请行并校验可退量。
     *
     * @param lines 行命令
     * @param order 来源订单
     * @param requestId 申请 ID（编辑时排除自身）
     * @return 申请行
     */
    private List<ReturnRequestItem> mapItems(List<ReturnRequestLineCommand> lines, SalesOrder order, Long requestId) {
        if (lines == null || lines.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "退换货申请至少需要一行明细");
        }
        Map<Integer, SalesOrderLine> orderLines = new LinkedHashMap<>();
        for (SalesOrderLine line : salesOrderRepository.findLines(order.getId())) {
            orderLines.put(line.getLineNo(), line);
        }
        Map<Integer, BigDecimal> requested = new LinkedHashMap<>();
        List<ReturnRequestItem> items = new ArrayList<>();
        int lineNo = 1;
        for (ReturnRequestLineCommand line : lines) {
            SalesOrderLine orderLine = orderLines.get(line.getOrderLineNo());
            if (orderLine == null) {
                throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                        "来源订单行不存在：" + line.getOrderLineNo());
            }
            BigDecimal quantity = line.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "退货数量必须大于 0");
            }
            BigDecimal already = requested.getOrDefault(line.getOrderLineNo(), BigDecimal.ZERO);
            BigDecimal occupied = returnRequestRepository.sumReturnedQuantity(order.getId(), line.getOrderLineNo(),
                    OCCUPIED_STATUSES);
            BigDecimal available = (orderLine.getQuantity() == null ? BigDecimal.ZERO : orderLine.getQuantity())
                    .subtract(occupied).subtract(already);
            if (quantity.compareTo(available) > 0) {
                throw CrmErrorCode.exception(CrmErrorCode.RETURN_QUANTITY_EXCEEDED,
                        "订单行 " + line.getOrderLineNo() + " 可退数量为 " + available.stripTrailingZeros().toPlainString());
            }
            requested.put(line.getOrderLineNo(), already.add(quantity));
            ReturnRequestItem item = new ReturnRequestItem();
            item.setRequestId(requestId);
            item.setLineNo(lineNo++);
            item.setOrderLineNo(line.getOrderLineNo());
            item.setItemCode(orderLine.getItemCode());
            item.setItemName(orderLine.getItemName());
            item.setQuantity(quantity);
            item.setUnitPrice(orderLine.getUnitPrice());
            item.setAmount(item.calculateAmount());
            items.add(item);
        }
        return items;
    }

    /**
     * 构建 ERP 退货命令。
     *
     * @param request 申请
     * @return ERP 命令
     */
    private ErpReturnCommand buildErpCommand(ReturnRequest request) {
        ErpReturnCommand command = new ErpReturnCommand();
        command.setCrmReturnNo(request.getReturnNo());
        command.setErpOrderNo(request.getErpOrderNo());
        command.setDocDate(LocalDate.now());
        command.setRemark(request.getReason());
        for (ReturnRequestItem item : request.getItems()) {
            ErpReturnCommand.Line line = new ErpReturnCommand.Line();
            line.setOrderLineNo(item.getOrderLineNo());
            line.setQuantity(item.getQuantity());
            command.getLines().add(line);
        }
        return command;
    }

    /**
     * 读取申请并校验数据范围。
     *
     * @param id 申请 ID
     * @param actor 当前操作人
     * @return 申请
     */
    private ReturnRequest requireRequest(Long id, CrmActor actor) {
        ReturnRequest request = returnRequestRepository.findById(id);
        if (request == null || (!actor.allDataScope() && !Objects.equals(request.getOwnerId(), actor.requireUserId()))) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_REQUEST_NOT_FOUND);
        }
        return request;
    }

    /**
     * 读取来源订单并校验数据范围。
     *
     * @param id 订单 ID
     * @param actor 当前操作人
     * @return 订单
     */
    private SalesOrder requireOrder(Long id, CrmActor actor) {
        SalesOrder order = id == null ? null : salesOrderRepository.findById(id);
        if (order == null || (!actor.allDataScope() && !Objects.equals(order.getOwnerId(), actor.requireUserId()))) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "来源商务订单不存在或无权访问");
        }
        if (!StringUtils.hasText(order.getErpOrderNo())) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT, "来源订单尚未下发 ERP，不能申请退货");
        }
        return order;
    }

    /**
     * 组装申请视图。
     *
     * @param request 申请
     * @return 视图
     */
    private ReturnRequestView view(ReturnRequest request) {
        return ReturnRequestView.from(request, returnRequestRepository.findItems(request.getId()));
    }

    /**
     * 汇总行金额。
     *
     * @param items 申请行
     * @return 总额
     */
    private BigDecimal totalAmount(List<ReturnRequestItem> items) {
        return items.stream().map(ReturnRequestItem::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 生成下一个申请单号。
     *
     * @return 申请单号
     */
    private String nextReturnNo() {
        String prefix = RETURN_NO_PREFIX + LocalDate.now().format(RETURN_DATE_FORMAT) + "-";
        long sequence = returnRequestRepository.maxSequence(prefix) + 1;
        return prefix + String.format("%04d", sequence);
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
     * 截断失败原因。
     *
     * @param message 原始消息
     * @return 截断后消息
     */
    private String truncate(String message) {
        return message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }

    /**
     * 服务门禁校验。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed() || !serviceProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }
}
