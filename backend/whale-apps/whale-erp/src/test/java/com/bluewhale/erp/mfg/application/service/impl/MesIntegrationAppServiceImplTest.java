package com.bluewhale.erp.mfg.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.mfg.application.command.MesCompletionCallback;
import com.bluewhale.erp.mfg.application.command.MesProgressCallback;
import com.bluewhale.erp.mfg.application.service.ProductionOrderAppService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ERP ↔ MES 集成服务单元测试。
 *
 * <p>覆盖下发成功/失败、完工回执幂等与拒绝、进度快照、对账差异标记。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesIntegrationAppServiceImplTest {

    @Mock
    private ProductionOrderRepository productionOrderRepository;

    @Mock
    private MesSyncRepository mesSyncRepository;

    @Mock
    private MesWorkOrderGateway mesWorkOrderGateway;

    @Mock
    private ProductionOrderAppService productionOrderAppService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MesIntegrationAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MesIntegrationAppServiceImpl(productionOrderRepository, mesSyncRepository,
                mesWorkOrderGateway, productionOrderAppService, transactionManager, objectMapper);
    }

    @Test
    @DisplayName("下发成功：标记 PUSHED 并记录工单编码")
    void shouldMarkPushed() {
        ProductionOrder order = order(MfgStatus.RELEASED);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(mesWorkOrderGateway.push(order)).thenReturn(new MesPushResult(9L, "MES-MO-1"));

        ProductionOrderView view = service.push(1L);

        assertEquals(MesStatus.PUSHED, view.getMesSyncStatus());
        assertEquals("MES-MO-1", view.getMesWorkOrderCode());
        verify(productionOrderRepository).updateMesSync(order);
    }

    @Test
    @DisplayName("下发失败：标记 FAILED 并保留错误摘要")
    void shouldMarkFailed() {
        ProductionOrder order = order(MfgStatus.RELEASED);
        when(productionOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(mesWorkOrderGateway.push(order)).thenThrow(new BizException("MES 服务不可用"));

        ProductionOrderView view = service.push(1L);

        assertEquals(MesStatus.FAILED, view.getMesSyncStatus());
        assertTrue(view.getMesLastError().contains("MES 服务不可用"));
        verify(productionOrderRepository).updateMesSync(order);
    }

    @Test
    @DisplayName("完工回传：重复事件直接返回 DUPLICATE")
    void shouldRejectDuplicateCompletion() {
        ProductionOrder order = order(MfgStatus.IN_PROGRESS);
        when(productionOrderRepository.findByOrderNo("MO-1")).thenReturn(Optional.of(order));
        when(mesSyncRepository.existsEvent("e-1")).thenReturn(true);

        service.handleCompletion("e-1", completionPayload("10"));

        ArgumentCaptor<MesCallbackLog> captor = ArgumentCaptor.forClass(MesCallbackLog.class);
        verify(mesSyncRepository).insertCallback(captor.capture());
        assertEquals(MesStatus.DUPLICATE, captor.getValue().getStatus());
        verify(productionOrderAppService, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("完工回传：按累计量差入库并记录 APPLIED")
    void shouldApplyCompletionDelta() {
        ProductionOrder order = order(MfgStatus.IN_PROGRESS);
        order.setCompletedQuantity(new BigDecimal("4"));
        when(productionOrderRepository.findByOrderNo("MO-1")).thenReturn(Optional.of(order));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service.handleCompletion("e-2", completionPayload("10"));

        ArgumentCaptor<MesCallbackLog> captor = ArgumentCaptor.forClass(MesCallbackLog.class);
        verify(mesSyncRepository).insertCallback(captor.capture());
        assertEquals(MesStatus.APPLIED, captor.getValue().getStatus());
        verify(productionOrderAppService).complete(any(), any(), any());
    }

    @Test
    @DisplayName("完工回传：订单状态不允许时拒绝")
    void shouldRejectCompletionByState() {
        ProductionOrder order = order(MfgStatus.COMPLETED);
        when(productionOrderRepository.findByOrderNo("MO-1")).thenReturn(Optional.of(order));

        service.handleCompletion("e-3", completionPayload("10"));

        ArgumentCaptor<MesCallbackLog> captor = ArgumentCaptor.forClass(MesCallbackLog.class);
        verify(mesSyncRepository).insertCallback(captor.capture());
        assertEquals(MesStatus.REJECTED, captor.getValue().getStatus());
        verify(productionOrderAppService, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("进度回传：覆盖快照并记录 APPLIED")
    void shouldApplyProgress() {
        ProductionOrder order = order(MfgStatus.IN_PROGRESS);
        when(productionOrderRepository.findByOrderNo("MO-1")).thenReturn(Optional.of(order));

        service.handleProgress("e-4", progressPayload());

        verify(mesSyncRepository).upsertProgress(any(MesProgressSnapshot.class));
        ArgumentCaptor<MesCallbackLog> captor = ArgumentCaptor.forClass(MesCallbackLog.class);
        verify(mesSyncRepository).insertCallback(captor.capture());
        assertEquals(MesStatus.APPLIED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("对账分页：完工数量不一致时标记差异")
    void shouldMarkMismatch() {
        ProductionOrder order = order(MfgStatus.IN_PROGRESS);
        order.setCompletedQuantity(new BigDecimal("4"));
        when(productionOrderRepository.pageForMes(any())).thenReturn(List.of(order));
        when(productionOrderRepository.countForMes(any())).thenReturn(1L);
        MesProgressSnapshot progress = new MesProgressSnapshot();
        progress.setCompletedQuantity(new BigDecimal("8"));
        when(mesSyncRepository.findProgress("MO-1")).thenReturn(Optional.of(progress));
        when(mesSyncRepository.listCallbacks("MO-1")).thenReturn(List.of());

        MesReconcileQuery query = new MesReconcileQuery();
        query.setCurrent(1L);
        query.setSize(20L);
        var page = service.reconcilePage(query);

        MesReconcileView view = page.getRecords().get(0);
        assertTrue(view.getCompletedMismatch());
        assertFalse(view.getCallbackRejected());
        assertEquals(1, page.getTotal());
    }

    /**
     * 构建生产订单。
     *
     * @param status 状态
     * @return 生产订单
     */
    private ProductionOrder order(String status) {
        ProductionOrder order = new ProductionOrder();
        order.setId(1L);
        order.setOrderNo("MO-1");
        order.setItemCode("P-1");
        order.setItemName("产品");
        order.setPlannedQuantity(new BigDecimal("10"));
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setStatus(status);
        order.setPlanStartDate(LocalDate.now());
        order.setMesSyncStatus(MesStatus.NOT_SYNCED);
        return order;
    }

    /**
     * 完工回传报文。
     *
     * @param completed 累计完工数量
     * @return JSON
     */
    private String completionPayload(String completed) {
        MesCompletionCallback callback = new MesCompletionCallback();
        callback.setErpOrderNo("MO-1");
        callback.setWorkOrderId(9L);
        callback.setWorkOrderCode("MES-MO-1");
        callback.setItemCode("P-1");
        callback.setCompletedQuantity(new BigDecimal(completed));
        try {
            return objectMapper.writeValueAsString(callback);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 进度回传报文。
     *
     * @return JSON
     */
    private String progressPayload() {
        MesProgressCallback callback = new MesProgressCallback();
        callback.setErpOrderNo("MO-1");
        callback.setWorkOrderId(9L);
        callback.setWorkOrderCode("MES-MO-1");
        callback.setStatus("IN_PROGRESS");
        callback.setPlannedQuantity(new BigDecimal("10"));
        callback.setReportedQuantity(new BigDecimal("3"));
        callback.setQualifiedQuantity(new BigDecimal("3"));
        callback.setCompletedQuantity(new BigDecimal("2"));
        callback.setTasks(List.of());
        try {
            return objectMapper.writeValueAsString(callback);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
