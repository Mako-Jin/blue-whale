package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.application.command.MesCompletionCallback;
import com.bluewhale.erp.mfg.application.command.MesProgressCallback;
import com.bluewhale.erp.mfg.application.command.ProductionCompleteCommand;
import com.bluewhale.erp.mfg.application.event.ProductionOrderReleasedEvent;
import com.bluewhale.erp.mfg.application.service.MesIntegrationAppService;
import com.bluewhale.erp.mfg.application.service.ProductionOrderAppService;
import com.bluewhale.erp.mfg.application.view.MesReconcileDetailView;
import com.bluewhale.erp.mfg.application.view.MesReconcileView;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MesCallbackLog;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.model.MesPushResult;
import com.bluewhale.erp.mfg.domain.model.MesReconcileQuery;
import com.bluewhale.erp.mfg.domain.model.MesStatus;
import com.bluewhale.erp.mfg.domain.model.MfgStatus;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.port.MesWorkOrderGateway;
import com.bluewhale.erp.mfg.domain.repository.MesSyncRepository;
import com.bluewhale.erp.mfg.domain.repository.ProductionOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ERP ↔ MES 集成应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesIntegrationAppServiceImpl implements MesIntegrationAppService {

    /** 错误摘要最大长度 */
    private static final int ERROR_MAX_LENGTH = 255;

    private final ProductionOrderRepository productionOrderRepository;

    private final MesSyncRepository mesSyncRepository;

    private final MesWorkOrderGateway mesWorkOrderGateway;

    private final ProductionOrderAppService productionOrderAppService;

    private final PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper;

    /**
     * 生产订单下达后自动下发 MES（事务提交后执行，失败不回滚下达）。
     *
     * @param event 下达事件
     */
    @Transactional(rollbackFor = Exception.class)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReleased(ProductionOrderReleasedEvent event) {
        try {
            push(event.orderId());
        } catch (RuntimeException e) {
            log.warn("生产订单 {} 下发 MES 失败: {}", event.orderId(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView push(Long orderId) {
        ProductionOrder order = requireOrder(orderId);
        try {
            MesPushResult result = mesWorkOrderGateway.push(order);
            order.markMesPushed(result.getWorkOrderCode(), LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("生产订单 {} 下发 MES 失败", order.getOrderNo(), e);
            order.markMesFailed(errorOf(e));
        }
        productionOrderRepository.updateMesSync(order);
        return ProductionOrderView.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesProgressSnapshot pull(Long orderId) {
        ProductionOrder order = requireOrder(orderId);
        Optional<MesProgressSnapshot> pulled;
        try {
            pulled = mesWorkOrderGateway.pull(order.getOrderNo());
        } catch (RuntimeException e) {
            order.markMesFailed(errorOf(e));
            productionOrderRepository.updateMesSync(order);
            return null;
        }
        if (pulled.isEmpty()) {
            order.markMesFailed("MES 未找到对应工单");
            productionOrderRepository.updateMesSync(order);
            return null;
        }
        MesProgressSnapshot snapshot = pulled.get();
        snapshot.setOrderId(order.getId());
        snapshot.setErpOrderNo(order.getOrderNo());
        snapshot.setEventId("manual-pull:" + order.getOrderNo() + ":" + System.currentTimeMillis());
        snapshot.setReceivedAt(LocalDateTime.now());
        mesSyncRepository.upsertProgress(snapshot);
        order.markMesPushed(snapshot.getWorkOrderCode(), LocalDateTime.now());
        productionOrderRepository.updateMesSync(order);
        return snapshot;
    }

    @Override
    public PageResult<MesReconcileView> reconcilePage(MesReconcileQuery query) {
        List<ProductionOrder> orders = productionOrderRepository.pageForMes(query);
        List<MesReconcileView> records = orders.stream().map(this::toReconcileView).toList();
        return PageResult.of(records, productionOrderRepository.countForMes(query),
                query.getCurrent(), query.getSize());
    }

    @Override
    public MesReconcileDetailView reconcileDetail(Long orderId) {
        ProductionOrder order = requireOrder(orderId);
        MesReconcileDetailView detail = new MesReconcileDetailView();
        detail.setOrder(ProductionOrderView.of(order));
        detail.setProgress(mesSyncRepository.findProgress(order.getOrderNo()).orElse(null));
        detail.setCallbacks(mesSyncRepository.listCallbacks(order.getOrderNo()));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCompletion(String eventId, String payload) {
        MesCompletionCallback callback;
        try {
            callback = objectMapper.readValue(payload, MesCompletionCallback.class);
        } catch (Exception e) {
            throw ErpErrorCode.exception(ErpErrorCode.MES_CALLBACK_INVALID, "完工回传报文解析失败");
        }
        if (!StringUtils.hasText(callback.getErpOrderNo()) || callback.getCompletedQuantity() == null) {
            throw ErpErrorCode.exception(ErpErrorCode.MES_CALLBACK_INVALID, "完工回传缺少订单号或完工数量");
        }
        Optional<ProductionOrder> found = productionOrderRepository.findByOrderNo(callback.getErpOrderNo());
        if (found.isEmpty()) {
            writeCallback(null, callback.getErpOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.REJECTED, payload, "ERP 无此生产订单");
            return;
        }
        ProductionOrder order = found.get();
        if (mesSyncRepository.existsEvent(eventId)) {
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.DUPLICATE, payload, null);
            return;
        }
        if (!MfgStatus.RELEASED.equals(order.getStatus()) && !MfgStatus.IN_PROGRESS.equals(order.getStatus())) {
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.REJECTED, payload, "订单状态 " + order.getStatus() + " 不允许完工回传");
            return;
        }
        BigDecimal delta = callback.getCompletedQuantity()
                .subtract(order.getCompletedQuantity() == null ? BigDecimal.ZERO : order.getCompletedQuantity());
        if (delta.signum() <= 0) {
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.DUPLICATE, payload, null);
            return;
        }
        try {
            applyCompletionInNewTransaction(order.getId(), delta);
            if (StringUtils.hasText(callback.getWorkOrderCode())) {
                order.markMesPushed(callback.getWorkOrderCode(), LocalDateTime.now());
                productionOrderRepository.updateMesSync(order);
            }
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.APPLIED, payload, null);
        } catch (RuntimeException e) {
            log.warn("生产订单 {} 完工回传应用失败: {}", order.getOrderNo(), e.getMessage());
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_COMPLETION, eventId,
                    MesStatus.REJECTED, payload, errorOf(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProgress(String eventId, String payload) {
        MesProgressCallback callback;
        try {
            callback = objectMapper.readValue(payload, MesProgressCallback.class);
        } catch (Exception e) {
            throw ErpErrorCode.exception(ErpErrorCode.MES_CALLBACK_INVALID, "进度回传报文解析失败");
        }
        if (!StringUtils.hasText(callback.getErpOrderNo())) {
            throw ErpErrorCode.exception(ErpErrorCode.MES_CALLBACK_INVALID, "进度回传缺少 ERP 订单号");
        }
        ProductionOrder order = productionOrderRepository.findByOrderNo(callback.getErpOrderNo()).orElse(null);
        if (order == null) {
            writeCallback(null, callback.getErpOrderNo(), MesStatus.CALLBACK_PROGRESS, eventId,
                    MesStatus.REJECTED, payload, "ERP 无此生产订单");
            return;
        }
        if (mesSyncRepository.existsEvent(eventId)) {
            writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_PROGRESS, eventId,
                    MesStatus.DUPLICATE, payload, null);
            return;
        }
        MesProgressSnapshot snapshot = new MesProgressSnapshot();
        snapshot.setOrderId(order.getId());
        snapshot.setErpOrderNo(order.getOrderNo());
        snapshot.setWorkOrderId(callback.getWorkOrderId());
        snapshot.setWorkOrderCode(callback.getWorkOrderCode());
        snapshot.setMesStatus(callback.getStatus());
        snapshot.setPlannedQuantity(callback.getPlannedQuantity());
        snapshot.setReportedQuantity(callback.getReportedQuantity());
        snapshot.setQualifiedQuantity(callback.getQualifiedQuantity());
        snapshot.setCompletedQuantity(callback.getCompletedQuantity());
        snapshot.setTasks(callback.getTasks());
        snapshot.setEventId(eventId);
        snapshot.setReceivedAt(LocalDateTime.now());
        mesSyncRepository.upsertProgress(snapshot);
        if (StringUtils.hasText(callback.getWorkOrderCode())) {
            order.markMesPushed(callback.getWorkOrderCode(), LocalDateTime.now());
            productionOrderRepository.updateMesSync(order);
        }
        writeCallback(order.getId(), order.getOrderNo(), MesStatus.CALLBACK_PROGRESS, eventId,
                MesStatus.APPLIED, payload, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderView applyCompletion(Long orderId, BigDecimal quantity, Long operatorId) {
        ProductionOrder order = requireOrder(orderId);
        if (!MfgStatus.RELEASED.equals(order.getStatus()) && !MfgStatus.IN_PROGRESS.equals(order.getStatus())) {
            throw ErpErrorCode.exception(ErpErrorCode.MES_CALLBACK_STATE_CONFLICT,
                    "订单状态 " + order.getStatus() + " 不允许完工入库");
        }
        ProductionCompleteCommand command = new ProductionCompleteCommand();
        command.setCompleteDate(LocalDate.now());
        command.setQuantity(quantity);
        return productionOrderAppService.complete(orderId, command, operatorId);
    }

    /**
     * 在独立事务中执行完工入库，避免失败污染回调流水事务。
     *
     * @param orderId 生产订单主键
     * @param quantity 本次入库数量
     */
    private void applyCompletionInNewTransaction(Long orderId, BigDecimal quantity) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            ProductionCompleteCommand command = new ProductionCompleteCommand();
            command.setCompleteDate(LocalDate.now());
            command.setQuantity(quantity);
            productionOrderAppService.complete(orderId, command, MesStatus.SYSTEM_OPERATOR_ID);
        });
    }

    /**
     * 写入回调流水。
     *
     * @param orderId      生产订单主键，可为空
     * @param erpOrderNo   ERP 订单号
     * @param callbackType 回调类型
     * @param eventId      事件键
     * @param status       处理结果
     * @param payload      原始报文
     * @param error        错误摘要
     */
    private void writeCallback(Long orderId, String erpOrderNo, String callbackType, String eventId,
                               String status, String payload, String error) {
        MesCallbackLog record = new MesCallbackLog();
        record.setOrderId(orderId);
        record.setErpOrderNo(erpOrderNo);
        record.setCallbackType(callbackType);
        record.setSourceEventId(eventId);
        record.setStatus(status);
        record.setPayload(payload);
        record.setError(error);
        record.setReceivedAt(LocalDateTime.now());
        mesSyncRepository.insertCallback(record);
    }

    /**
     * 构建对账视图。
     *
     * @param order 生产订单
     * @return 对账行
     */
    private MesReconcileView toReconcileView(ProductionOrder order) {
        MesReconcileView view = new MesReconcileView();
        view.setOrderId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setItemCode(order.getItemCode());
        view.setItemName(order.getItemName());
        view.setPlannedQuantity(order.getPlannedQuantity());
        view.setCompletedQuantity(order.getCompletedQuantity());
        view.setStatus(order.getStatus());
        view.setPlanStartDate(order.getPlanStartDate());
        view.setMesSyncStatus(order.getMesSyncStatus());
        view.setMesWorkOrderCode(order.getMesWorkOrderCode());
        view.setMesSyncedAt(order.getMesSyncedAt());
        view.setMesLastError(order.getMesLastError());
        Optional<MesProgressSnapshot> progress = mesSyncRepository.findProgress(order.getOrderNo());
        progress.ifPresent(snapshot -> {
            view.setMesStatus(snapshot.getMesStatus());
            view.setMesReportedQuantity(snapshot.getReportedQuantity());
            view.setMesQualifiedQuantity(snapshot.getQualifiedQuantity());
            view.setMesCompletedQuantity(snapshot.getCompletedQuantity());
            view.setMesReceivedAt(snapshot.getReceivedAt());
            BigDecimal mesCompleted = snapshot.getCompletedQuantity();
            BigDecimal erpCompleted = order.getCompletedQuantity();
            boolean mismatch = mesCompleted != null && erpCompleted != null
                    && mesCompleted.compareTo(erpCompleted) != 0;
            view.setCompletedMismatch(mismatch);
        });
        List<MesCallbackLog> callbacks = mesSyncRepository.listCallbacks(order.getOrderNo());
        callbacks.stream().max(Comparator.comparing(MesCallbackLog::getReceivedAt)).ifPresent(last -> {
            view.setLastCallbackStatus(last.getStatus());
            view.setLastCallbackError(last.getError());
            view.setCallbackRejected(MesStatus.REJECTED.equals(last.getStatus()));
        });
        if (view.getCallbackRejected() == null) {
            view.setCallbackRejected(Boolean.FALSE);
        }
        if (view.getCompletedMismatch() == null) {
            view.setCompletedMismatch(Boolean.FALSE);
        }
        return view;
    }

    /**
     * 加载生产订单。
     *
     * @param orderId 主键
     * @return 生产订单
     */
    private ProductionOrder requireOrder(Long orderId) {
        return productionOrderRepository.findById(orderId)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PRODUCTION_ORDER_NOT_FOUND));
    }

    /**
     * 提取异常摘要。
     *
     * @param e 异常
     * @return 截断后的摘要
     */
    private String errorOf(RuntimeException e) {
        String message = e instanceof BizException && StringUtils.hasText(e.getMessage())
                ? e.getMessage() : "MES 调用失败";
        return message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }
}
