package com.bluewhale.mes.dispatch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dispatch.dto.MesDispatchRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskQueryRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskSplitRequest;
import com.bluewhale.mes.dispatch.dto.MesWorkOrderReceiveRequest;
import com.bluewhale.mes.dispatch.entity.MesDispatchLine;
import com.bluewhale.mes.dispatch.entity.MesDispatchOrder;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesDispatchLineMapper;
import com.bluewhale.mes.dispatch.mapper.MesDispatchOrderMapper;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesTaskProgressService;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.execution.entity.MesExceptionEvent;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesExceptionEventMapper;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.masterdata.entity.MesProductionLine;
import com.bluewhale.mes.masterdata.entity.MesRoutingOperation;
import com.bluewhale.mes.masterdata.entity.MesRoutingSnapshot;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.mapper.MesProductionLineMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingSnapshotMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 调度派工服务单元测试（M1）。
 *
 * <p>覆盖 ERP 工单接收幂等、工单拆分（自动/手工与数量校验）、批量派工、撤回限制、
 * 分页截断与生产看板达成率，Mapper 以 Mock 隔离，不依赖 MySQL。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesDispatchServiceImplTest {

    /** 测试工单 ID */
    private static final Long WORK_ORDER_ID = 1L;

    @Mock
    private MesWorkOrderMapper mesWorkOrderMapper;

    @Mock
    private MesWorkOrderTaskMapper taskMapper;

    @Mock
    private MesDispatchOrderMapper dispatchOrderMapper;

    @Mock
    private MesDispatchLineMapper dispatchLineMapper;

    @Mock
    private MesProductionLineMapper lineMapper;

    @Mock
    private MesTeamMapper teamMapper;

    @Mock
    private MesWorkCenterMapper workCenterMapper;

    @Mock
    private MesRoutingSnapshotMapper routingSnapshotMapper;

    @Mock
    private MesRoutingOperationMapper routingOperationMapper;

    @Mock
    private MesProductionReportMapper reportMapper;

    @Mock
    private MesExceptionEventMapper exceptionMapper;

    @Mock
    private MesTaskProgressService progressService;

    @InjectMocks
    private MesDispatchServiceImpl dispatchService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesWorkOrderTask.class, MesWorkOrder.class, MesDispatchOrder.class,
                MesDispatchLine.class, MesProductionReport.class, MesExceptionEvent.class,
                MesRoutingSnapshot.class, MesRoutingOperation.class, MesWorkCenter.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * ERP 工单首次下发：默认 RECEIVED、NORMAL、version=1，编码按 ERP 单号生成
     */
    @Test
    @DisplayName("接收 ERP 工单：首次建单默认值正确")
    void receiveShouldCreateWorkOrderWithDefaults() {
        MesWorkOrderReceiveRequest request = receiveRequest("PO-1001", "50");
        when(mesWorkOrderMapper.selectOne(any())).thenReturn(null);

        MesWorkOrderVO vo = dispatchService.receive(request);

        ArgumentCaptor<MesWorkOrder> captor = ArgumentCaptor.forClass(MesWorkOrder.class);
        verify(mesWorkOrderMapper).insert(captor.capture());
        MesWorkOrder created = captor.getValue();
        assertEquals("MES-PO-1001", created.getCode());
        assertEquals("RECEIVED", created.getStatus());
        assertEquals("NORMAL", created.getPriority());
        assertEquals(Integer.valueOf(1), created.getVersion());
        assertEquals(request.getPlannedQuantity(), created.getPlannedQuantity());
        assertEquals(created.getId(), vo.getId());
    }

    /**
     * 同一 ERP 单号重复下发：已接收工单按新计划更新，不重复建单
     */
    @Test
    @DisplayName("接收 ERP 工单：幂等更新已接收工单")
    void receiveShouldUpdateReceivedWorkOrder() {
        MesWorkOrderReceiveRequest request = receiveRequest("PO-1002", "80");
        MesWorkOrder existing = workOrder("RECEIVED", "50");
        when(mesWorkOrderMapper.selectOne(any())).thenReturn(existing);

        dispatchService.receive(request);

        verify(mesWorkOrderMapper, never()).insert(any(MesWorkOrder.class));
        verify(mesWorkOrderMapper).updateById(existing);
        assertEquals(new BigDecimal("80"), existing.getPlannedQuantity());
    }

    /**
     * 已派工工单修改计划数量：拒绝并提示
     */
    @Test
    @DisplayName("接收 ERP 工单：已派工拒绝改量")
    void receiveShouldRejectQuantityChangeAfterDispatched() {
        MesWorkOrderReceiveRequest request = receiveRequest("PO-1003", "80");
        MesWorkOrder existing = workOrder("DISPATCHED", "50");
        when(mesWorkOrderMapper.selectOne(any())).thenReturn(existing);

        BizException exception = assertThrows(BizException.class, () -> dispatchService.receive(request));

        assertTrue(exception.getMessage().contains("不允许修改计划数量"));
        verify(mesWorkOrderMapper, never()).updateById(any(MesWorkOrder.class));
    }

    /**
     * 工单自动拆分：按已发布工艺路线生成每道工序任务
     */
    @Test
    @DisplayName("工单拆分：按工艺路线自动生成任务")
    void splitShouldDeriveFromRouting() {
        MesWorkOrder workOrder = workOrder("RECEIVED", "100");
        when(mesWorkOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        MesRoutingSnapshot snapshot = new MesRoutingSnapshot();
        snapshot.setId(9L);
        when(routingSnapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(routingOperationMapper.selectList(any())).thenReturn(List.of(
                operation(1, "OP10"), operation(2, "OP20")));

        List<?> tasks = dispatchService.split(WORK_ORDER_ID, new MesTaskSplitRequest());

        assertEquals(2, tasks.size());
        verify(taskMapper, org.mockito.Mockito.times(2)).insert(any(MesWorkOrderTask.class));
    }

    /**
     * 手工拆分：同一工序数量合计不等于工单计划量时拒绝
     */
    @Test
    @DisplayName("工单拆分：分量合计不等于计划量时拒绝")
    void splitShouldRejectQuantityMismatch() {
        MesWorkOrder workOrder = workOrder("RECEIVED", "100");
        when(mesWorkOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        MesTaskSplitRequest request = new MesTaskSplitRequest();
        MesTaskSplitRequest.Item item = new MesTaskSplitRequest.Item();
        item.setSeqNo(1);
        item.setOperationCode("OP10");
        item.setPlannedQuantity(new BigDecimal("50"));
        request.setItems(List.of(item));

        BizException exception = assertThrows(BizException.class,
                () -> dispatchService.split(WORK_ORDER_ID, request));

        assertTrue(exception.getMessage().contains("应等于工单计划数量"));
        verify(taskMapper, never()).insert(any(MesWorkOrderTask.class));
    }

    /**
     * 重复拆分工单：拒绝
     */
    @Test
    @DisplayName("工单拆分：已拆分拒绝重复操作")
    void splitShouldRejectWhenAlreadySplit() {
        when(mesWorkOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder("RECEIVED", "100"));
        when(taskMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> dispatchService.split(WORK_ORDER_ID, new MesTaskSplitRequest()));

        assertTrue(exception.getMessage().contains("已拆分"));
    }

    /**
     * 批量派工：任务置为 DISPATCHED、写派工明细并回写工单状态
     */
    @Test
    @DisplayName("批量派工：生成派工单并回写工单状态")
    void dispatchShouldCreateOrderAndUpdateTask() {
        MesWorkOrderTask task = task("PLANNED", "100");
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(task));
        MesProductionLine line = new MesProductionLine();
        line.setId(2L);
        line.setCode("L1");
        line.setStatus("ENABLED");
        when(lineMapper.selectById(2L)).thenReturn(line);
        when(mesWorkOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder("RECEIVED", "100"));
        when(taskMapper.updateById(any(MesWorkOrderTask.class))).thenReturn(1);
        when(mesWorkOrderMapper.updateById(any(MesWorkOrder.class))).thenReturn(1);
        MesDispatchRequest request = new MesDispatchRequest();
        request.setTaskIds(List.of(11L));
        request.setLineId(2L);

        String dispatchNo = dispatchService.dispatch(request);

        assertTrue(dispatchNo.startsWith("DSP-"));
        verify(dispatchOrderMapper).insert(any(MesDispatchOrder.class));
        verify(dispatchLineMapper).insert(any(MesDispatchLine.class));
        assertEquals("DISPATCHED", task.getStatus());
        assertEquals(new BigDecimal("100"), task.getDispatchedQuantity());
    }

    /**
     * 撤回派工：任务已开工时拒绝
     */
    @Test
    @DisplayName("撤回派工：任务已开工时拒绝")
    void withdrawShouldRejectStartedTask() {
        MesDispatchOrder order = new MesDispatchOrder();
        order.setId(5L);
        order.setWorkOrderId(WORK_ORDER_ID);
        order.setStatus("DISPATCHED");
        when(dispatchOrderMapper.selectById(5L)).thenReturn(order);
        MesDispatchLine line = new MesDispatchLine();
        line.setDispatchOrderId(5L);
        line.setTaskId(11L);
        line.setTaskNo("T-1");
        when(dispatchLineMapper.selectList(any())).thenReturn(List.of(line));
        when(taskMapper.selectById(11L)).thenReturn(task("IN_PROGRESS", "100"));

        BizException exception = assertThrows(BizException.class, () -> dispatchService.withdraw(5L));

        assertTrue(exception.getMessage().contains("已开工"));
        verify(dispatchOrderMapper, never()).updateById(any(MesDispatchOrder.class));
    }

    /**
     * 任务分页：每页条数上限截断为 200
     */
    @Test
    @DisplayName("任务分页：页大小截断为 200")
    void pageTasksShouldClampPageSize() {
        MesTaskQueryRequest request = new MesTaskQueryRequest();
        request.setSize(500L);
        when(taskMapper.selectPage(any(), any())).thenReturn(new Page<>());

        PageResult<?> result = dispatchService.pageTasks(request);

        ArgumentCaptor<Page<MesWorkOrderTask>> captor = ArgumentCaptor.forClass(Page.class);
        verify(taskMapper).selectPage(captor.capture(), any());
        assertEquals(200L, captor.getValue().getSize());
        assertEquals(0L, result.getTotal());
    }

    /**
     * 生产看板：按当日报工汇总达成率与异常数
     */
    @Test
    @DisplayName("生产看板：汇总目标、完成数与达成率")
    void boardShouldAggregateDailyMetrics() {
        MesWorkOrderTask task = task("DISPATCHED", "100");
        task.setPlannedStart(null);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        MesProductionReport report = new MesProductionReport();
        report.setQualifiedQuantity(new BigDecimal("50"));
        report.setDefectQuantity(new BigDecimal("5"));
        when(reportMapper.selectList(any())).thenReturn(List.of(report));
        when(exceptionMapper.selectCount(any())).thenReturn(2L);

        MesBoardVO board = dispatchService.board(null, null);

        assertEquals(new BigDecimal("100"), board.getTargetQuantity());
        assertEquals(new BigDecimal("50"), board.getCompletedQuantity());
        assertEquals(new BigDecimal("5"), board.getDefectQuantity());
        assertEquals(new BigDecimal("50.00"), board.getAchievementRate());
        assertEquals(2L, board.getExceptionCount());
        assertEquals(0L, board.getActiveTaskCount());
    }

    /**
     * 构建下发请求
     *
     * @param erpOrderNo ERP 单号
     * @param quantity   计划数量
     * @return 下发请求
     */
    private MesWorkOrderReceiveRequest receiveRequest(String erpOrderNo, String quantity) {
        MesWorkOrderReceiveRequest request = new MesWorkOrderReceiveRequest();
        request.setErpOrderNo(erpOrderNo);
        request.setItemCode("ITEM-1");
        request.setItemName("测试物料");
        request.setPlannedQuantity(new BigDecimal(quantity));
        request.setUom("PCS");
        request.setPlannedStart(LocalDateTime.of(2026, 11, 4, 8, 0));
        request.setPlannedEnd(LocalDateTime.of(2026, 11, 4, 18, 0));
        return request;
    }

    /**
     * 构建工单
     *
     * @param status   状态
     * @param quantity 计划数量
     * @return 工单
     */
    private MesWorkOrder workOrder(String status, String quantity) {
        MesWorkOrder workOrder = new MesWorkOrder();
        workOrder.setId(WORK_ORDER_ID);
        workOrder.setCode("WO-1");
        workOrder.setErpOrderNo("PO-1");
        workOrder.setItemCode("ITEM-1");
        workOrder.setPlannedQuantity(new BigDecimal(quantity));
        workOrder.setStatus(status);
        workOrder.setPriority("NORMAL");
        workOrder.setVersion(1);
        return workOrder;
    }

    /**
     * 构建任务
     *
     * @param status   状态
     * @param quantity 计划数量
     * @return 任务
     */
    private MesWorkOrderTask task(String status, String quantity) {
        MesWorkOrderTask task = new MesWorkOrderTask();
        task.setId(11L);
        task.setTaskNo("T-1");
        task.setWorkOrderId(WORK_ORDER_ID);
        task.setWorkOrderCode("WO-1");
        task.setSeqNo(1);
        task.setOperationCode("OP10");
        task.setOperationName("工序 10");
        task.setPlannedQuantity(new BigDecimal(quantity));
        task.setDispatchedQuantity("PLANNED".equals(status) ? BigDecimal.ZERO : new BigDecimal(quantity));
        task.setReportedQuantity(BigDecimal.ZERO);
        task.setQualifiedQuantity(BigDecimal.ZERO);
        task.setDefectQuantity(BigDecimal.ZERO);
        task.setCompletedQuantity(BigDecimal.ZERO);
        task.setScrappedQuantity(BigDecimal.ZERO);
        task.setStatus(status);
        task.setPriority("NORMAL");
        task.setVersion(1);
        return task;
    }

    /**
     * 构建工艺路线工序
     *
     * @param seqNo         工序顺序
     * @param operationCode 工序编码
     * @return 工序快照
     */
    private MesRoutingOperation operation(int seqNo, String operationCode) {
        MesRoutingOperation operation = new MesRoutingOperation();
        operation.setRoutingId(9L);
        operation.setSeqNo(seqNo);
        operation.setOperationCode(operationCode);
        operation.setOperationName(operationCode);
        return operation;
    }
}
