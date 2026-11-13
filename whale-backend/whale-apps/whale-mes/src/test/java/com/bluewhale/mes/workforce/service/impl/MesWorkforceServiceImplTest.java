package com.bluewhale.mes.workforce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.dispatch.entity.MesWorkOrderTask;
import com.bluewhale.mes.dispatch.mapper.MesWorkOrderTaskMapper;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.masterdata.entity.MesRoutingOperation;
import com.bluewhale.mes.masterdata.entity.MesRoutingSnapshot;
import com.bluewhale.mes.masterdata.entity.MesStation;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.mapper.MesRoutingOperationMapper;
import com.bluewhale.mes.masterdata.mapper.MesRoutingSnapshotMapper;
import com.bluewhale.mes.masterdata.mapper.MesStationMapper;
import com.bluewhale.mes.masterdata.mapper.MesTeamMapper;
import com.bluewhale.mes.masterdata.mapper.MesWorkCenterMapper;
import com.bluewhale.mes.workforce.dto.MesHandoverConfirmRequest;
import com.bluewhale.mes.workforce.dto.MesPerformanceAggregateRequest;
import com.bluewhale.mes.workforce.dto.MesShiftAssignmentSaveRequest;
import com.bluewhale.mes.workforce.dto.MesWorkHourSaveRequest;
import com.bluewhale.mes.workforce.entity.MesHandover;
import com.bluewhale.mes.workforce.entity.MesPerformanceStat;
import com.bluewhale.mes.workforce.entity.MesShiftAssignment;
import com.bluewhale.mes.workforce.entity.MesSkillMatrix;
import com.bluewhale.mes.workforce.entity.MesTeamMember;
import com.bluewhale.mes.workforce.entity.MesWorkHour;
import com.bluewhale.mes.workforce.mapper.MesHandoverMapper;
import com.bluewhale.mes.workforce.mapper.MesPerformanceStatMapper;
import com.bluewhale.mes.workforce.mapper.MesShiftAssignmentMapper;
import com.bluewhale.mes.workforce.mapper.MesSkillMatrixMapper;
import com.bluewhale.mes.workforce.mapper.MesTeamMemberMapper;
import com.bluewhale.mes.workforce.mapper.MesWorkHourMapper;
import com.bluewhale.mes.workforce.vo.MesPerformanceStatVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 人员绩效服务单元测试（M5）。
 *
 * <p>覆盖主操作员唯一、净工时非负、月度锁定后禁止重算、跨租户记录不可见（按 404 处理）
 * 与任务不存在拒绝登记五条核心不变量。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesWorkforceServiceImplTest {

    @Mock
    private MesTeamMemberMapper teamMemberMapper;

    @Mock
    private MesShiftAssignmentMapper shiftAssignmentMapper;

    @Mock
    private MesHandoverMapper handoverMapper;

    @Mock
    private MesWorkHourMapper workHourMapper;

    @Mock
    private MesPerformanceStatMapper performanceStatMapper;

    @Mock
    private MesSkillMatrixMapper skillMatrixMapper;

    @Mock
    private MesTeamMapper teamMapper;

    @Mock
    private MesStationMapper stationMapper;

    @Mock
    private MesWorkCenterMapper workCenterMapper;

    @Mock
    private MesWorkOrderTaskMapper taskMapper;

    @Mock
    private MesProductionReportMapper reportMapper;

    @Mock
    private MesRoutingSnapshotMapper routingSnapshotMapper;

    @Mock
    private MesRoutingOperationMapper routingOperationMapper;

    @InjectMocks
    private MesWorkforceServiceImpl workforceService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    /**
     * 注册本类用到的全部实体 TableInfo（MyBatis-Plus 的 lambda 列缓存）。
     *
     * <p>单测无 Spring/MyBatis 会话，{@code LambdaQueryWrapper} 的 {@code ::getXxx} 依赖
     * {@link TableInfoHelper} 里的元数据；漏注册的实体会抛
     * {@code can not find lambda cache for this entity}。而该缓存是 JVM 级共享的，
     * 之前是否有别的测试先初始化过它取决于执行顺序 —— 因此必须在本类显式全量注册，
     * 不能依赖「本地能过」（CI 与本地用例顺序不同，曾因此单点飘红）。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesTeamMember.class, MesShiftAssignment.class, MesHandover.class,
                MesWorkHour.class, MesPerformanceStat.class, MesSkillMatrix.class,
                MesProductionReport.class, MesRoutingOperation.class, MesRoutingSnapshot.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Test
    @DisplayName("同一工位班次仅允许一名主操作员")
    void shouldRejectSecondPrimaryOperator() {
        MesTeam team = new MesTeam();
        team.setId(11L);
        team.setCode("TEAM-A");
        MesStation station = new MesStation();
        station.setId(21L);
        station.setCode("ST-01");
        station.setWorkCenterId(31L);
        when(teamMapper.selectById(11L)).thenReturn(team);
        when(stationMapper.selectById(21L)).thenReturn(station);
        when(shiftAssignmentMapper.selectCount(any())).thenReturn(0L, 1L);

        MesShiftAssignmentSaveRequest request = new MesShiftAssignmentSaveRequest();
        request.setShiftDate(LocalDate.of(2026, 11, 5));
        request.setShiftCode("DAY");
        request.setTeamId(11L);
        request.setStationId(21L);
        request.setEmployeeId(41L);
        request.setEmployeeName("张三");
        request.setPrimaryOperator(1);

        BizException exception = assertThrows(BizException.class,
                () -> workforceService.saveShiftAssignment(request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(shiftAssignmentMapper, never()).insert(any(MesShiftAssignment.class));
    }

    @Test
    @DisplayName("净工时不得为负")
    void shouldRejectNegativeNetSeconds() {
        MesWorkOrderTask task = new MesWorkOrderTask();
        task.setId(51L);
        task.setTaskNo("TASK-001");
        task.setWorkOrderId(61L);
        task.setWorkOrderCode("WO-001");
        when(taskMapper.selectById(51L)).thenReturn(task);

        MesWorkHourSaveRequest request = new MesWorkHourSaveRequest();
        request.setEmployeeId(41L);
        request.setEmployeeName("张三");
        request.setWorkDate(LocalDate.of(2026, 11, 5));
        request.setTaskId(51L);
        request.setStartAt(LocalDateTime.of(2026, 11, 5, 10, 0));
        request.setEndAt(LocalDateTime.of(2026, 11, 5, 9, 0));

        BizException exception = assertThrows(BizException.class,
                () -> workforceService.registerWorkHour(request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(workHourMapper, never()).insert(any(MesWorkHour.class));
    }

    @Test
    @DisplayName("月度锁定后禁止重算绩效")
    void shouldRejectAggregateWhenPeriodLocked() {
        when(performanceStatMapper.selectCount(any())).thenReturn(1L);
        MesPerformanceAggregateRequest request = new MesPerformanceAggregateRequest();
        request.setStatLevel("MONTHLY");
        request.setPeriodCode("202611");

        BizException exception = assertThrows(BizException.class,
                () -> workforceService.aggregatePerformance(request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(workHourMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("同一任务多人报工按工时占比分摊产量（D13）")
    void shouldAllocateOutputByWorkHourRatio() {
        MesWorkHour first = workHourRow(41L, "张三", 6000L);
        MesWorkHour second = workHourRow(42L, "李四", 4000L);
        when(workHourMapper.selectList(any())).thenReturn(List.of(first, second));
        MesProductionReport report = new MesProductionReport();
        report.setTaskId(51L);
        report.setReportQuantity(new BigDecimal("100"));
        report.setQualifiedQuantity(new BigDecimal("95"));
        report.setDefectQuantity(new BigDecimal("5"));
        report.setStatus("SUBMITTED");
        report.setReportedAt(LocalDateTime.of(2026, 11, 5, 17, 0));
        when(reportMapper.selectList(any())).thenReturn(List.of(report));

        MesPerformanceAggregateRequest request = new MesPerformanceAggregateRequest();
        request.setStatLevel("DAILY");
        request.setPeriodCode("20261105");
        List<MesPerformanceStatVO> result = workforceService.aggregatePerformance(request);

        assertEquals(2, result.size());
        MesPerformanceStatVO firstStat = result.stream()
                .filter(vo -> Long.valueOf(41L).equals(vo.getEmployeeId())).findFirst().orElseThrow();
        MesPerformanceStatVO secondStat = result.stream()
                .filter(vo -> Long.valueOf(42L).equals(vo.getEmployeeId())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("60.000000"), firstStat.getProductionQuantity());
        assertEquals(new BigDecimal("57.000000"), firstStat.getQualifiedQuantity());
        assertEquals(new BigDecimal("40.000000"), secondStat.getProductionQuantity());
        assertEquals(new BigDecimal("38.000000"), secondStat.getQualifiedQuantity());
    }

    /**
     * 构造一条有效工时记录
     *
     * @param employeeId 员工 ID
     * @param employeeName 员工姓名
     * @param netSeconds 净工时秒数
     * @return 工时记录
     */
    private MesWorkHour workHourRow(Long employeeId, String employeeName, long netSeconds) {
        MesWorkHour row = new MesWorkHour();
        row.setEmployeeId(employeeId);
        row.setEmployeeName(employeeName);
        row.setTaskId(51L);
        row.setWorkDate(LocalDate.of(2026, 11, 5));
        row.setNetSeconds(netSeconds);
        row.setStatus("VALID");
        return row;
    }

    @Test
    @DisplayName("跨租户交接单不可见时按 404 处理")
    void shouldTreatInvisibleHandoverAsNotFound() {
        when(handoverMapper.selectById(71L)).thenReturn(null);
        MesHandoverConfirmRequest request = new MesHandoverConfirmRequest();
        request.setConfirmedBy("李四");

        BizException exception = assertThrows(BizException.class,
                () -> workforceService.confirmHandover(71L, request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("任务不存在时拒绝工时登记")
    void shouldRejectWorkHourWhenTaskMissing() {
        when(taskMapper.selectById(51L)).thenReturn(null);
        MesWorkHourSaveRequest request = new MesWorkHourSaveRequest();
        request.setEmployeeId(41L);
        request.setEmployeeName("张三");
        request.setWorkDate(LocalDate.of(2026, 11, 5));
        request.setTaskId(51L);
        request.setStartAt(LocalDateTime.of(2026, 11, 5, 8, 0));
        request.setEndAt(LocalDateTime.of(2026, 11, 5, 9, 0));

        BizException exception = assertThrows(BizException.class,
                () -> workforceService.registerWorkHour(request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(workHourMapper, never()).insert(any(MesWorkHour.class));
    }
}
