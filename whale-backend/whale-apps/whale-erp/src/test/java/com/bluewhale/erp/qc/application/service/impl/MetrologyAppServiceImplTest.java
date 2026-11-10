package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.qc.application.command.CalibrationPlanSaveCommand;
import com.bluewhale.erp.qc.application.command.CalibrationRecordCommand;
import com.bluewhale.erp.qc.application.command.MeasuringToolSaveCommand;
import com.bluewhale.erp.qc.application.view.CalibrationRecordView;
import com.bluewhale.erp.qc.application.view.MeasuringToolView;
import com.bluewhale.erp.qc.domain.model.CalibrationPlan;
import com.bluewhale.erp.qc.domain.model.CalibrationRecord;
import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QcStatus;
import com.bluewhale.erp.qc.domain.repository.MetrologyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MetrologyAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class MetrologyAppServiceImplTest {

    private final MetrologyRepository metrologyRepository = mock(MetrologyRepository.class);

    private final MetrologyAppServiceImpl service = new MetrologyAppServiceImpl(metrologyRepository);

    @Test
    @DisplayName("新增量具按上次校准日推算下次校准日")
    void shouldCreateTool() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.empty());

        MeasuringToolView view = service.createTool(toolCommand());

        assertEquals(LocalDate.of(2027, 11, 4), view.getNextCalibrationDate());
        assertEquals(QcStatus.ENABLED, view.getStatus());
        verify(metrologyRepository).insertTool(any(MeasuringTool.class));
    }

    @Test
    @DisplayName("量具编号重复拒绝建档")
    void shouldRejectDuplicateTool() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.of(tool()));

        BizException exception = assertThrows(BizException.class, () -> service.createTool(toolCommand()));

        assertEquals(ErpErrorCode.TOOL_CODE_CONFLICT, exception.getCode());
        verify(metrologyRepository, never()).insertTool(any(MeasuringTool.class));
    }

    @Test
    @DisplayName("编辑不存在的量具返回 404")
    void shouldRejectUpdateMissingTool() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.updateTool("T001", toolCommand()));

        assertEquals(ErpErrorCode.TOOL_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("校准计划必须关联已存在量具")
    void shouldRejectPlanForMissingTool() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.createPlan(planCommand()));

        assertEquals(ErpErrorCode.TOOL_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("校准合格回写量具并顺延计划")
    void shouldAdvanceToolAndPlanOnPass() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.of(tool()));
        when(metrologyRepository.findPlanById(5L)).thenReturn(Optional.of(plan()));
        when(metrologyRepository.nextRecordNo(any())).thenReturn("CALR-202611-0001");
        CalibrationRecordCommand command = recordCommand(QcStatus.RESULT_PASS);
        command.setPlanId(5L);

        CalibrationRecordView view = service.calibrate(command);

        assertEquals(QcStatus.RESULT_PASS, view.getResult());
        ArgumentCaptor<MeasuringTool> toolCaptor = ArgumentCaptor.forClass(MeasuringTool.class);
        verify(metrologyRepository).updateTool(toolCaptor.capture());
        assertEquals(LocalDate.of(2027, 11, 4), toolCaptor.getValue().getNextCalibrationDate());
        ArgumentCaptor<CalibrationPlan> planCaptor = ArgumentCaptor.forClass(CalibrationPlan.class);
        verify(metrologyRepository).updatePlan(planCaptor.capture());
        assertEquals(LocalDate.of(2027, 11, 4), planCaptor.getValue().getNextDate());
    }

    @Test
    @DisplayName("校准不合格不回写量具日期")
    void shouldNotAdvanceOnFail() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.of(tool()));
        when(metrologyRepository.nextRecordNo(any())).thenReturn("CALR-202611-0001");

        CalibrationRecordView view = service.calibrate(recordCommand(QcStatus.RESULT_FAIL));

        assertEquals(QcStatus.RESULT_FAIL, view.getResult());
        verify(metrologyRepository, never()).updateTool(any(MeasuringTool.class));
    }

    @Test
    @DisplayName("校准计划与量具不匹配时拒绝")
    void shouldRejectMismatchedPlan() {
        when(metrologyRepository.findToolByNo("T001")).thenReturn(Optional.of(tool()));
        CalibrationPlan other = plan();
        other.setToolNo("T002");
        when(metrologyRepository.findPlanById(5L)).thenReturn(Optional.of(other));
        CalibrationRecordCommand command = recordCommand(QcStatus.RESULT_PASS);
        command.setPlanId(5L);

        BizException exception = assertThrows(BizException.class, () -> service.calibrate(command));

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
        verify(metrologyRepository, never()).insertRecord(any(CalibrationRecord.class));
    }

    @Test
    @DisplayName("量具分页返回总数")
    void shouldPageTools() {
        QcQuery query = new QcQuery();
        when(metrologyRepository.pageTools(query)).thenReturn(List.of(tool()));
        when(metrologyRepository.countTools(query)).thenReturn(1L);

        assertEquals(1L, service.pageTools(query).getTotal());
    }

    /**
     * 构造量具命令。
     *
     * @return 命令
     */
    private MeasuringToolSaveCommand toolCommand() {
        MeasuringToolSaveCommand command = new MeasuringToolSaveCommand();
        command.setToolNo("T001");
        command.setName("卡尺");
        command.setCalibrationCycleMonths(12);
        command.setLastCalibrationDate(LocalDate.of(2026, 11, 4));
        return command;
    }

    /**
     * 构造校准计划命令。
     *
     * @return 命令
     */
    private CalibrationPlanSaveCommand planCommand() {
        CalibrationPlanSaveCommand command = new CalibrationPlanSaveCommand();
        command.setToolNo("T001");
        command.setCycleMonths(12);
        command.setNextDate(LocalDate.of(2026, 11, 4));
        return command;
    }

    /**
     * 构造校准记录命令。
     *
     * @param result 校准结论
     * @return 命令
     */
    private CalibrationRecordCommand recordCommand(String result) {
        CalibrationRecordCommand command = new CalibrationRecordCommand();
        command.setToolNo("T001");
        command.setCalibrationDate(LocalDate.of(2026, 11, 4));
        command.setResult(result);
        return command;
    }

    /**
     * 构造量具。
     *
     * @return 量具
     */
    private MeasuringTool tool() {
        MeasuringTool tool = new MeasuringTool();
        tool.setId(1L);
        tool.setToolNo("T001");
        tool.setName("卡尺");
        tool.setCalibrationCycleMonths(12);
        tool.setLastCalibrationDate(LocalDate.of(2025, 11, 4));
        tool.setNextCalibrationDate(LocalDate.of(2026, 11, 4));
        tool.setStatus(QcStatus.ENABLED);
        return tool;
    }

    /**
     * 构造校准计划。
     *
     * @return 校准计划
     */
    private CalibrationPlan plan() {
        CalibrationPlan plan = new CalibrationPlan();
        plan.setId(5L);
        plan.setPlanNo("CALP-202611-0001");
        plan.setToolNo("T001");
        plan.setCycleMonths(12);
        plan.setNextDate(LocalDate.of(2026, 11, 4));
        plan.setStatus(QcStatus.ENABLED);
        return plan;
    }
}
