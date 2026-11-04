package com.bluewhale.mes.execution.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.dispatch.service.MesTaskProgressService;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.execution.dto.MesExceptionActionRequest;
import com.bluewhale.mes.execution.dto.MesExceptionCreateRequest;
import com.bluewhale.mes.execution.dto.MesMaterialIssueRequest;
import com.bluewhale.mes.execution.dto.MesReportRequest;
import com.bluewhale.mes.execution.dto.MesTaskActionRequest;
import com.bluewhale.mes.execution.entity.MesExceptionAction;
import com.bluewhale.mes.execution.entity.MesExceptionEvent;
import com.bluewhale.mes.execution.entity.MesMaterialIssue;
import com.bluewhale.mes.execution.entity.MesMaterialIssueLine;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.entity.MesReportDefect;
import com.bluewhale.mes.execution.entity.MesWipSnapshot;
import com.bluewhale.mes.execution.mapper.MesExceptionActionMapper;
import com.bluewhale.mes.execution.mapper.MesExceptionEventMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialIssueLineMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialIssueMapper;
import com.bluewhale.mes.execution.mapper.MesMaterialReturnMapper;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.execution.mapper.MesReportDefectMapper;
import com.bluewhale.mes.execution.mapper.MesWipSnapshotMapper;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.masterdata.entity.MesReasonCode;
import com.bluewhale.mes.masterdata.mapper.MesReasonCodeMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 生产执行服务单元测试（M1）。
 *
 * <p>覆盖任务状态迁移、报工数量校验、在制品重算、异常状态机与领料登记，
 * Mapper 以 Mock 隔离，不依赖 MySQL。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesExecutionServiceImplTest {

    /** 测试工单 ID */
    private static final Long WORK_ORDER_ID = 1L;

    /** 测试任务 ID */
    private static final Long TASK_ID = 11L;

    @Mock
    private MesWorkOrderTaskMapper taskMapper;

    @Mock
    private MesWorkOrderMapper mesWorkOrderMapper;

    @Mock
    private MesProductionReportMapper reportMapper;

    @Mock
    private MesReportDefectMapper reportDefectMapper;

    @Mock
    private MesWipSnapshotMapper wipMapper;

    @Mock
    private MesExceptionEventMapper exceptionMapper;

    @Mock
    private MesExceptionActionMapper exceptionActionMapper;

    @Mock
    private MesMaterialIssueMapper materialIssueMapper;

    @Mock
    private MesMaterialIssueLineMapper materialIssueLineMapper;

    @Mock
    private MesMaterialReturnMapper materialReturnMapper;

    @Mock
    private MesStationMapper stationMapper;

    @Mock
    private MesReasonCodeMapper reasonCodeMapper;

    @Mock
    private MesTaskProgressService progressService;

    @InjectMocks
    private MesExecutionServiceImpl executionService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesWorkOrderTask.class, MesProductionReport.class,
                MesWipSnapshot.class, MesReasonCode.class, MesExceptionEvent.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 开工：DISPATCHED → IN_PROGRESS 并触发工单回流
     */
    @Test
    @DisplayName("任务开工：已派工可开工")
    void startShouldTransitionFromDispatched() {
        MesWorkOrderTask task = task("DISPATCHED");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(MesWorkOrderTask.class))).thenReturn(1);

        executionService.start(TASK_ID, new MesTaskActionRequest());

        assertEquals("IN_PROGRESS", task.getStatus());
        verify(progressService).refreshWorkOrder(WORK_ORDER_ID);
    }

    /**
     * 开工：待派工状态拒绝
     */
    @Test
    @DisplayName("任务开工：未派工拒绝")
    void startShouldRejectPlannedTask() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("PLANNED"));

        BizException exception = assertThrows(BizException.class,
                () -> executionService.start(TASK_ID, new MesTaskActionRequest()));

        assertTrue(exception.getMessage().contains("不允许开工"));
        verify(taskMapper, never()).updateById(any(MesWorkOrderTask.class));
    }

    /**
     * 报工：累加合格/不良数量，写入不良明细与在制品快照
     */
    @Test
    @DisplayName("扫码报工：累加数量并写不良明细")
    void reportShouldAppendTotalsAndDefects() {
        MesWorkOrderTask task = task("IN_PROGRESS");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(MesWorkOrderTask.class))).thenReturn(1);
        when(wipMapper.selectOne(any())).thenReturn(null);

        MesReportRequest request = reportRequest("10", "9", "1");
        MesReportRequest.DefectItem defect = new MesReportRequest.DefectItem();
        defect.setDefectCode("D1");
        defect.setQuantity(new BigDecimal("1"));
        request.setDefects(List.of(defect));

        executionService.report(request);

        verify(reportMapper).insert(any(MesProductionReport.class));
        verify(reportDefectMapper).insert(any(MesReportDefect.class));
        verify(wipMapper).insert(any(MesWipSnapshot.class));
        assertEquals(new BigDecimal("10"), task.getReportedQuantity());
        assertEquals(new BigDecimal("9"), task.getQualifiedQuantity());
        assertEquals(new BigDecimal("1"), task.getDefectQuantity());
    }

    /**
     * 报工：合格 + 不良不等于报工数时拒绝
     */
    @Test
    @DisplayName("扫码报工：数量口径不一致拒绝")
    void reportShouldRejectQuantityMismatch() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("IN_PROGRESS"));

        MesReportRequest request = reportRequest("10", "8", "1");

        BizException exception = assertThrows(BizException.class, () -> executionService.report(request));

        assertTrue(exception.getMessage().contains("必须等于报工数量"));
    }

    /**
     * 报工：累计超过已派工数量时拒绝
     */
    @Test
    @DisplayName("扫码报工：超派工数量拒绝")
    void reportShouldRejectOverDispatched() {
        MesWorkOrderTask task = task("IN_PROGRESS");
        task.setReportedQuantity(new BigDecimal("95"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        BizException exception = assertThrows(BizException.class,
                () -> executionService.report(reportRequest("10", "10", "0")));

        assertTrue(exception.getMessage().contains("超过已派工数量"));
    }

    /**
     * 完工：完工数量取累计合格数
     */
    @Test
    @DisplayName("任务完工：完工数量取合格数")
    void completeShouldUseQualifiedQuantity() {
        MesWorkOrderTask task = task("IN_PROGRESS");
        task.setReportedQuantity(new BigDecimal("10"));
        task.setQualifiedQuantity(new BigDecimal("9"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(MesWorkOrderTask.class))).thenReturn(1);
        when(wipMapper.selectOne(any())).thenReturn(null);

        executionService.complete(TASK_ID, new MesTaskActionRequest());

        assertEquals(new BigDecimal("9"), task.getCompletedQuantity());
        assertEquals("COMPLETED", task.getStatus());
    }

    /**
     * 报废：超过剩余可执行数量时拒绝
     */
    @Test
    @DisplayName("任务报废：超量拒绝")
    void scrapShouldRejectOverRemaining() {
        MesWorkOrderTask task = task("IN_PROGRESS");
        task.setScrappedQuantity(new BigDecimal("90"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        MesTaskActionRequest request = new MesTaskActionRequest();
        request.setQuantity(new BigDecimal("20"));
        request.setRemark("设备故障");

        BizException exception = assertThrows(BizException.class,
                () -> executionService.scrap(TASK_ID, request));

        assertTrue(exception.getMessage().contains("超过任务剩余可执行数量"));
    }

    /**
     * 异常：确认后解决，状态按状态机迁移并记录动作
     */
    @Test
    @DisplayName("异常处理：确认与解决状态迁移")
    void exceptionShouldFollowStateMachine() {
        MesExceptionEvent event = new MesExceptionEvent();
        event.setId(3L);
        event.setStatus("OPEN");
        when(exceptionMapper.selectById(3L)).thenReturn(event);
        when(exceptionMapper.updateById(any(MesExceptionEvent.class))).thenReturn(1);

        MesExceptionActionRequest acknowledge = new MesExceptionActionRequest();
        acknowledge.setActionType("ACKNOWLEDGE");
        executionService.handleException(3L, acknowledge);
        assertEquals("ACKNOWLEDGED", event.getStatus());

        MesExceptionActionRequest resolve = new MesExceptionActionRequest();
        resolve.setActionType("RESOLVE");
        resolve.setResolution("已更换刀具");
        executionService.handleException(3L, resolve);
        assertEquals("RESOLVED", event.getStatus());
        assertNotNull(event.getResolvedAt());

        verify(exceptionActionMapper, times(2)).insert(any(MesExceptionAction.class));
    }

    /**
     * 异常上报：未关联工单与任务时拒绝
     */
    @Test
    @DisplayName("异常上报：必须关联工单或任务")
    void raiseExceptionShouldRequireAssociation() {
        MesExceptionCreateRequest request = new MesExceptionCreateRequest();
        request.setExceptionType("EQUIPMENT");
        request.setDescription("设备停机");

        BizException exception = assertThrows(BizException.class,
                () -> executionService.raiseException(request));

        assertTrue(exception.getMessage().contains("必须关联工单或任务"));
    }

    /**
     * 领料：写领料单与明细
     */
    @Test
    @DisplayName("领料登记：写领料单与明细")
    void issueMaterialShouldCreateIssueAndLines() {
        MesWorkOrder workOrder = new MesWorkOrder();
        workOrder.setId(WORK_ORDER_ID);
        when(mesWorkOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder);
        MesMaterialIssueRequest request = new MesMaterialIssueRequest();
        request.setWorkOrderId(WORK_ORDER_ID);
        request.setWarehouseCode("WH01");
        MesMaterialIssueRequest.Line line = new MesMaterialIssueRequest.Line();
        line.setItemCode("M-1");
        line.setQuantity(new BigDecimal("5"));
        request.setLines(List.of(line));

        String issueNo = executionService.issueMaterial(request);

        assertTrue(issueNo.startsWith("MIS-"));
        verify(materialIssueMapper).insert(any(MesMaterialIssue.class));
        verify(materialIssueLineMapper).insert(any(MesMaterialIssueLine.class));
    }

    /**
     * 构建任务
     *
     * @param status 状态
     * @return 任务
     */
    private MesWorkOrderTask task(String status) {
        MesWorkOrderTask task = new MesWorkOrderTask();
        task.setId(TASK_ID);
        task.setTaskNo("T-1");
        task.setWorkOrderId(WORK_ORDER_ID);
        task.setWorkOrderCode("WO-1");
        task.setSeqNo(1);
        task.setOperationCode("OP10");
        task.setOperationName("工序 10");
        task.setPlannedQuantity(new BigDecimal("100"));
        task.setDispatchedQuantity(new BigDecimal("100"));
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
     * 构建报工请求
     *
     * @param report    报工数量
     * @param qualified 合格数量
     * @param defect    不良数量
     * @return 报工请求
     */
    private MesReportRequest reportRequest(String report, String qualified, String defect) {
        MesReportRequest request = new MesReportRequest();
        request.setTaskId(TASK_ID);
        request.setReportQuantity(new BigDecimal(report));
        request.setQualifiedQuantity(new BigDecimal(qualified));
        request.setDefectQuantity(new BigDecimal(defect));
        request.setReporter("operator");
        return request;
    }
}
