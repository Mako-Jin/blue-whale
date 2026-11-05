package com.bluewhale.mes.dashboard.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import com.bluewhale.mes.dispatch.service.MesDispatchService;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.entity.MesOeeSnapshot;
import com.bluewhale.mes.equipment.mapper.MesEquipmentDowntimeMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import com.bluewhale.mes.equipment.mapper.MesOeeSnapshotMapper;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import com.bluewhale.mes.workforce.entity.MesPerformanceStat;
import com.bluewhale.mes.workforce.entity.MesWorkHour;
import com.bluewhale.mes.workforce.mapper.MesHandoverMapper;
import com.bluewhale.mes.workforce.mapper.MesPerformanceStatMapper;
import com.bluewhale.mes.workforce.mapper.MesShiftAssignmentMapper;
import com.bluewhale.mes.workforce.mapper.MesWorkHourMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * MES 综合看板服务单元测试（M5-B）。
 *
 * <p>覆盖四域聚合口径：生产/质量与独立看板同源同实例、设备状态计数与 OEE 均值、
 * 人员当日工时合计与当月锁定计数、日期参数校验与导出内容。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesDashboardServiceImplTest {

    @Mock
    private MesDispatchService dispatchService;

    @Mock
    private MesInspectionService inspectionService;

    @Mock
    private MesEquipmentMapper equipmentMapper;

    @Mock
    private MesEquipmentDowntimeMapper downtimeMapper;

    @Mock
    private MesOeeSnapshotMapper oeeSnapshotMapper;

    @Mock
    private MesProductionReportMapper productionReportMapper;

    @Mock
    private MesWorkHourMapper workHourMapper;

    @Mock
    private MesShiftAssignmentMapper shiftAssignmentMapper;

    @Mock
    private MesHandoverMapper handoverMapper;

    @Mock
    private MesPerformanceStatMapper performanceStatMapper;

    @InjectMocks
    private MesDashboardServiceImpl service;

    @Test
    @DisplayName("生产与质量域复用独立看板服务，口径一致")
    void dashboardShouldReuseProductionAndQualityBoards() {
        MesBoardVO production = new MesBoardVO();
        production.setCompletedQuantity(BigDecimal.TEN);
        MesQualityBoardVO quality = new MesQualityBoardVO();
        quality.setNcrOpenCount(2L);
        when(dispatchService.board(7L, 9L)).thenReturn(production);
        when(inspectionService.board(anyString(), eq(7L))).thenReturn(quality);

        MesDashboardVO vo = service.dashboard(7L, 9L, null);

        assertSame(production, vo.getProduction());
        assertSame(quality, vo.getQuality());
        assertEquals(LocalDate.now(), vo.getBoardDate());
    }

    @Test
    @DisplayName("设备域按状态计数并计算 OEE 均值")
    void dashboardShouldAggregateEquipmentBoard() {
        MesEquipment running = new MesEquipment();
        running.setStatus("RUNNING");
        running.setEnabled(1);
        MesEquipment fault = new MesEquipment();
        fault.setStatus("FAULT");
        fault.setEnabled(1);
        MesEquipment offline = new MesEquipment();
        offline.setStatus(null);
        offline.setEnabled(0);
        when(equipmentMapper.selectList(any())).thenReturn(List.of(running, fault, offline));
        when(downtimeMapper.selectCount(any())).thenReturn(2L);

        MesOeeSnapshot first = new MesOeeSnapshot();
        first.setAvailability(new BigDecimal("0.80"));
        first.setPerformance(new BigDecimal("0.90"));
        first.setQuality(new BigDecimal("0.95"));
        first.setOee(new BigDecimal("0.684"));
        MesOeeSnapshot second = new MesOeeSnapshot();
        second.setAvailability(new BigDecimal("0.60"));
        second.setPerformance(new BigDecimal("0.70"));
        second.setQuality(new BigDecimal("1.0000"));
        second.setOee(null);
        when(oeeSnapshotMapper.selectList(any())).thenReturn(List.of(first, second));

        MesDashboardVO vo = service.dashboard(null, null, null);

        assertEquals(3L, vo.getEquipment().getTotalEquipmentCount());
        assertEquals(2L, vo.getEquipment().getEnabledEquipmentCount());
        assertEquals(1L, vo.getEquipment().getStatusCounts().get("RUNNING"));
        assertEquals(1L, vo.getEquipment().getStatusCounts().get("UNKNOWN"));
        assertEquals(1L, vo.getEquipment().getFaultEquipmentCount());
        assertEquals(2L, vo.getEquipment().getTodayDowntimeCount());
        assertEquals(2L, vo.getEquipment().getOeeSnapshotCount());
        assertEquals(0, new BigDecimal("0.7000").compareTo(vo.getEquipment().getAvgAvailability()));
        assertEquals(0, new BigDecimal("0.8000").compareTo(vo.getEquipment().getAvgPerformance()));
        assertEquals(0, new BigDecimal("0.9750").compareTo(vo.getEquipment().getAvgQuality()));
        assertEquals(0, new BigDecimal("0.6840").compareTo(vo.getEquipment().getAvgOee()));
    }

    @Test
    @DisplayName("人员域聚合当日工时与当月绩效锁定进度")
    void dashboardShouldAggregateWorkforceBoard() {
        when(productionReportMapper.selectCount(any())).thenReturn(3L);
        MesWorkHour first = new MesWorkHour();
        first.setNetSeconds(3600L);
        MesWorkHour second = new MesWorkHour();
        second.setNetSeconds(null);
        when(workHourMapper.selectList(any())).thenReturn(List.of(first, second));
        when(shiftAssignmentMapper.selectCount(any())).thenReturn(5L);
        when(handoverMapper.selectCount(any())).thenReturn(1L);

        MesPerformanceStat locked = new MesPerformanceStat();
        locked.setLocked(1);
        locked.setProductionQuantity(new BigDecimal("100"));
        locked.setEfficiency(new BigDecimal("0.90"));
        locked.setPassRate(new BigDecimal("0.98"));
        MesPerformanceStat unlocked = new MesPerformanceStat();
        unlocked.setLocked(0);
        unlocked.setProductionQuantity(new BigDecimal("50"));
        unlocked.setEfficiency(null);
        unlocked.setPassRate(new BigDecimal("0.95"));
        when(performanceStatMapper.selectList(any())).thenReturn(List.of(locked, unlocked));

        MesDashboardVO vo = service.dashboard(null, null, null);

        assertEquals(3L, vo.getWorkforce().getTodayReportCount());
        assertEquals(2L, vo.getWorkforce().getTodayWorkHourCount());
        assertEquals(3600L, vo.getWorkforce().getTodayNetWorkSeconds());
        assertEquals(5L, vo.getWorkforce().getActiveShiftCount());
        assertEquals(1L, vo.getWorkforce().getDraftHandoverCount());
        assertEquals(2L, vo.getWorkforce().getMonthlyStatCount());
        assertEquals(1L, vo.getWorkforce().getMonthlyLockedCount());
        assertEquals(0, new BigDecimal("150").compareTo(vo.getWorkforce().getMonthlyProductionQuantity()));
        assertEquals(0, new BigDecimal("0.9000").compareTo(vo.getWorkforce().getMonthlyAvgEfficiency()));
        assertEquals(0, new BigDecimal("0.9650").compareTo(vo.getWorkforce().getMonthlyAvgPassRate()));
    }

    @Test
    @DisplayName("日期参数非法抛参数错误")
    void dashboardShouldRejectInvalidDate() {
        assertThrows(BizException.class, () -> service.dashboard(null, null, "2026/11/05"));
    }

    @Test
    @DisplayName("导出 CSV 包含四域标题与关键指标")
    void exportCsvShouldContainAllDomains() {
        MesBoardVO production = new MesBoardVO();
        production.setTargetQuantity(new BigDecimal("120"));
        when(dispatchService.board(null, null)).thenReturn(production);
        when(inspectionService.board(anyString(), any())).thenReturn(new MesQualityBoardVO());
        when(equipmentMapper.selectList(any())).thenReturn(List.of());
        when(downtimeMapper.selectCount(any())).thenReturn(0L);
        when(oeeSnapshotMapper.selectList(any())).thenReturn(List.of());
        when(productionReportMapper.selectCount(any())).thenReturn(0L);
        when(workHourMapper.selectList(any())).thenReturn(List.of());
        when(shiftAssignmentMapper.selectCount(any())).thenReturn(0L);
        when(handoverMapper.selectCount(any())).thenReturn(0L);
        when(performanceStatMapper.selectList(any())).thenReturn(List.of());

        String csv = service.exportCsv(null, null, null);

        assertTrue(csv.contains("MES 综合看板"));
        assertTrue(csv.contains("【生产域】"));
        assertTrue(csv.contains("【质量域】"));
        assertTrue(csv.contains("【设备域】"));
        assertTrue(csv.contains("【人员域】"));
        assertTrue(csv.contains("今日目标数量,120"));
    }
}
