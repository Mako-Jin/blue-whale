package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.crm.commerce.application.command.InvoiceApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.QuoteApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.command.SalesOrderApprovalCallbackCommand;
import com.bluewhale.crm.commerce.application.service.ApprovalReconcileAppService;
import com.bluewhale.crm.commerce.application.service.InvoiceAppService;
import com.bluewhale.crm.commerce.application.service.QuoteAppService;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;
import com.bluewhale.crm.commerce.domain.model.InvoiceStatus;
import com.bluewhale.crm.commerce.domain.model.QuoteApproval;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderStatus;
import com.bluewhale.crm.commerce.domain.repository.InvoiceRequestRepository;
import com.bluewhale.crm.commerce.domain.repository.QuoteApprovalRepository;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.commerce.domain.port.WorkflowStatusPort;
import com.bluewhale.crm.service.application.command.ReturnApprovalCallbackCommand;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnStatus;
import com.bluewhale.crm.service.domain.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批结果对账应用服务实现。
 *
 * <p>流程：聚合四类单据（商务订单 / 报价 / 开票申请 / 退货申请）的待审批流程实例 ID →
 * 一次批量查询 workflow 实例状态 → 对已通过 / 已驳回实例触发各自的幂等回调；
 * 已撤销实例不自动推进（业务上需人工确认单据去向），仅记 warn。</p>
 *
 * <p>回调实现均为幂等（条件更新仅 PENDING 命中），重复对账不会产生副作用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalReconcileAppServiceImpl implements ApprovalReconcileAppService {

    /** 对账回调携带的审批意见 */
    private static final String RECONCILE_COMMENT = "审批结果对账同步";

    private final SalesOrderRepository salesOrderRepository;

    private final InvoiceRequestRepository invoiceRequestRepository;

    private final ReturnRequestRepository returnRequestRepository;

    private final QuoteApprovalRepository quoteApprovalRepository;

    private final SalesOrderAppService salesOrderAppService;

    private final QuoteAppService quoteAppService;

    private final InvoiceAppService invoiceAppService;

    private final ReturnRequestAppService returnRequestAppService;

    private final WorkflowStatusPort workflowStatusPort;

    @Override
    public int reconcileAll() {
        Map<Long, Integer> statusMap = queryFinishedStatuses();
        if (statusMap.isEmpty()) {
            return 0;
        }
        int synced = reconcileOrders(statusMap) + reconcileInvoices(statusMap)
                + reconcileReturns(statusMap) + reconcileQuotes(statusMap);
        if (synced > 0) {
            log.info("审批结果对账完成，推进 {} 单", synced);
        }
        return synced;
    }

    /**
     * 聚合四类单据的待审批实例 ID 并批量查询状态。
     *
     * @return 实例 ID → 状态码
     */
    private Map<Long, Integer> queryFinishedStatuses() {
        List<Long> instanceIds = new ArrayList<>();
        salesOrderRepository.findByStatuses(
                        List.of(SalesOrderStatus.PENDING_APPROVAL.name()),
                        WorkflowStatusPort.MAX_BATCH_SIZE).stream()
                .map(SalesOrder::getApprovalInstanceId).filter(id -> id != null).forEach(instanceIds::add);
        invoiceRequestRepository.findByStatuses(
                        List.of(InvoiceStatus.PENDING_APPROVAL.name()),
                        WorkflowStatusPort.MAX_BATCH_SIZE).stream()
                .map(InvoiceRequest::getApprovalInstanceId).filter(id -> id != null).forEach(instanceIds::add);
        returnRequestRepository.findByStatuses(
                        List.of(ReturnStatus.PENDING_APPROVAL.name()),
                        WorkflowStatusPort.MAX_BATCH_SIZE).stream()
                .map(ReturnRequest::getApprovalInstanceId).filter(id -> id != null).forEach(instanceIds::add);
        quoteApprovalRepository.findPendingBatch(WorkflowStatusPort.MAX_BATCH_SIZE).stream()
                .map(QuoteApproval::getInstanceId).filter(id -> id != null).forEach(instanceIds::add);
        return workflowStatusPort.batchStatus(instanceIds);
    }

    /**
     * 对账商务订单：已办结实例触发幂等回调。
     *
     * @param statusMap 实例状态映射
     * @return 推进单数
     */
    private int reconcileOrders(Map<Long, Integer> statusMap) {
        int synced = 0;
        for (SalesOrder order : salesOrderRepository.findByStatuses(
                List.of(SalesOrderStatus.PENDING_APPROVAL.name()),
                WorkflowStatusPort.MAX_BATCH_SIZE)) {
            Integer status = resolveFinished(order.getApprovalInstanceId(), statusMap, "crm-order", order.getId());
            if (status == null) {
                continue;
            }
            TenantContext.set(order.getTenantId());
            try {
                SalesOrderApprovalCallbackCommand command = new SalesOrderApprovalCallbackCommand();
                command.setInstanceId(order.getApprovalInstanceId());
                command.setApproved(status == WorkflowStatusPort.STATUS_APPROVED);
                command.setComment(RECONCILE_COMMENT);
                if (salesOrderAppService.handleApprovalCallback(command)) {
                    synced++;
                }
            } catch (Exception exception) {
                log.warn("商务订单审批对账失败 orderId={}", order.getId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return synced;
    }

    /**
     * 对账开票申请：已办结实例触发幂等回调。
     *
     * @param statusMap 实例状态映射
     * @return 推进单数
     */
    private int reconcileInvoices(Map<Long, Integer> statusMap) {
        int synced = 0;
        for (InvoiceRequest request : invoiceRequestRepository.findByStatuses(
                List.of(InvoiceStatus.PENDING_APPROVAL.name()),
                WorkflowStatusPort.MAX_BATCH_SIZE)) {
            Integer status = resolveFinished(request.getApprovalInstanceId(), statusMap, "crm-invoice", request.getId());
            if (status == null) {
                continue;
            }
            TenantContext.set(request.getTenantId());
            try {
                InvoiceApprovalCallbackCommand command = new InvoiceApprovalCallbackCommand();
                command.setInstanceId(request.getApprovalInstanceId());
                command.setApproved(status == WorkflowStatusPort.STATUS_APPROVED);
                command.setComment(RECONCILE_COMMENT);
                if (invoiceAppService.handleApprovalCallback(command)) {
                    synced++;
                }
            } catch (Exception exception) {
                log.warn("开票申请审批对账失败 requestId={}", request.getId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return synced;
    }

    /**
     * 对账退货申请：已办结实例触发幂等回调。
     *
     * @param statusMap 实例状态映射
     * @return 推进单数
     */
    private int reconcileReturns(Map<Long, Integer> statusMap) {
        int synced = 0;
        for (ReturnRequest request : returnRequestRepository.findByStatuses(
                List.of(ReturnStatus.PENDING_APPROVAL.name()),
                WorkflowStatusPort.MAX_BATCH_SIZE)) {
            Integer status = resolveFinished(request.getApprovalInstanceId(), statusMap, "crm-return", request.getId());
            if (status == null) {
                continue;
            }
            TenantContext.set(request.getTenantId());
            try {
                ReturnApprovalCallbackCommand command = new ReturnApprovalCallbackCommand();
                command.setInstanceId(request.getApprovalInstanceId());
                command.setApproved(status == WorkflowStatusPort.STATUS_APPROVED);
                command.setComment(RECONCILE_COMMENT);
                if (returnRequestAppService.handleApprovalCallback(command)) {
                    synced++;
                }
            } catch (Exception exception) {
                log.warn("退货申请审批对账失败 requestId={}", request.getId(), exception);
            } finally {
                TenantContext.clear();
            }
        }
        return synced;
    }

    /**
     * 对账报价审批：已办结实例触发幂等回调（报价审批记录不带租户，走无租户上下文直查）。
     *
     * @param statusMap 实例状态映射
     * @return 推进单数
     */
    private int reconcileQuotes(Map<Long, Integer> statusMap) {
        int synced = 0;
        for (QuoteApproval approval : quoteApprovalRepository.findPendingBatch(
                WorkflowStatusPort.MAX_BATCH_SIZE)) {
            Integer status = resolveFinished(approval.getInstanceId(), statusMap, "crm-quote", approval.getQuoteId());
            if (status == null) {
                continue;
            }
            try {
                QuoteApprovalCallbackCommand command = new QuoteApprovalCallbackCommand();
                command.setInstanceId(approval.getInstanceId());
                command.setStatus(status);
                command.setComment(RECONCILE_COMMENT);
                if (quoteAppService.handleApprovalCallback(command)) {
                    synced++;
                }
            } catch (Exception exception) {
                log.warn("报价审批对账失败 quoteId={}", approval.getQuoteId(), exception);
            }
        }
        return synced;
    }

    /**
     * 解析已办结实例状态；运行中返回 null，已撤销记 warn 返回 null（不自动推进）。
     *
     * @param instanceId 流程实例 ID
     * @param statusMap 实例状态映射
     * @param docType 单据类型（日志用）
     * @param docId 单据 ID（日志用）
     * @return 状态码；无需推进返回 {@code null}
     */
    private Integer resolveFinished(Long instanceId, Map<Long, Integer> statusMap, String docType, Long docId) {
        if (instanceId == null) {
            return null;
        }
        Integer status = statusMap.get(instanceId);
        if (status == null || status == WorkflowStatusPort.STATUS_RUNNING) {
            return null;
        }
        if (status == WorkflowStatusPort.STATUS_CANCELLED) {
            log.warn("流程实例已撤销，单据保持待审批待人工处理 docType={} docId={} instanceId={}",
                    docType, docId, instanceId);
            return null;
        }
        return status;
    }
}
