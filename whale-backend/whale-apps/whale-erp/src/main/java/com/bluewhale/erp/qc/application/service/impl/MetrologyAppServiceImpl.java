package com.bluewhale.erp.qc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.qc.application.command.CalibrationPlanSaveCommand;
import com.bluewhale.erp.qc.application.command.CalibrationRecordCommand;
import com.bluewhale.erp.qc.application.command.MeasuringToolSaveCommand;
import com.bluewhale.erp.qc.application.service.MetrologyAppService;
import com.bluewhale.erp.qc.application.view.CalibrationPlanView;
import com.bluewhale.erp.qc.application.view.CalibrationRecordView;
import com.bluewhale.erp.qc.application.view.MeasuringToolView;
import com.bluewhale.erp.qc.domain.model.CalibrationPlan;
import com.bluewhale.erp.qc.domain.model.CalibrationRecord;
import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QcStatus;
import com.bluewhale.erp.qc.domain.repository.MetrologyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 计量器具与校准应用服务实现。
 *
 * <p>校准合格时回写量具上次/下次校准日期并顺延校准计划；校准不合格时保持量具原日期，
 * 使其继续处于过期不可用状态，直至重新校准合格。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MetrologyAppServiceImpl implements MetrologyAppService {

    private final MetrologyRepository metrologyRepository;

    @Override
    public PageResult<MeasuringToolView> pageTools(QcQuery query) {
        List<MeasuringToolView> records = metrologyRepository.pageTools(query).stream()
                .map(MeasuringToolView::of).toList();
        return PageResult.of(records, metrologyRepository.countTools(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasuringToolView createTool(MeasuringToolSaveCommand command) {
        if (metrologyRepository.findToolByNo(command.getToolNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.TOOL_CODE_CONFLICT);
        }
        MeasuringTool tool = new MeasuringTool();
        tool.setToolNo(command.getToolNo().trim());
        applyTool(tool, command);
        tool.validateForCreate();
        metrologyRepository.insertTool(tool);
        return MeasuringToolView.of(tool);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasuringToolView updateTool(String toolNo, MeasuringToolSaveCommand command) {
        MeasuringTool tool = requireTool(toolNo);
        applyTool(tool, command);
        tool.validateForCreate();
        metrologyRepository.updateTool(tool);
        return MeasuringToolView.of(tool);
    }

    @Override
    public PageResult<CalibrationPlanView> pagePlans(QcQuery query) {
        List<CalibrationPlanView> records = metrologyRepository.pagePlans(query).stream()
                .map(CalibrationPlanView::of).toList();
        return PageResult.of(records, metrologyRepository.countPlans(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CalibrationPlanView createPlan(CalibrationPlanSaveCommand command) {
        requireTool(command.getToolNo());
        CalibrationPlan plan = new CalibrationPlan();
        plan.setPlanNo(metrologyRepository.nextPlanNo(LocalDate.now()));
        applyPlan(plan, command);
        plan.validateForCreate();
        metrologyRepository.insertPlan(plan);
        return CalibrationPlanView.of(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CalibrationPlanView updatePlan(Long id, CalibrationPlanSaveCommand command) {
        CalibrationPlan plan = requirePlan(id);
        requireTool(command.getToolNo());
        applyPlan(plan, command);
        plan.validateForCreate();
        metrologyRepository.updatePlan(plan);
        return CalibrationPlanView.of(plan);
    }

    @Override
    public PageResult<CalibrationRecordView> pageRecords(QcQuery query) {
        List<CalibrationRecordView> records = metrologyRepository.pageRecords(query).stream()
                .map(CalibrationRecordView::of).toList();
        return PageResult.of(records, metrologyRepository.countRecords(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CalibrationRecordView calibrate(CalibrationRecordCommand command) {
        MeasuringTool tool = requireTool(command.getToolNo());
        CalibrationPlan plan = null;
        if (command.getPlanId() != null) {
            plan = requirePlan(command.getPlanId());
            if (!plan.getToolNo().equals(command.getToolNo())) {
                throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "校准计划与量具编号不匹配");
            }
        }
        CalibrationRecord record = new CalibrationRecord();
        record.setRecordNo(metrologyRepository.nextRecordNo(command.getCalibrationDate()));
        record.setToolNo(command.getToolNo());
        record.setPlanId(plan == null ? null : plan.getId());
        record.setCalibrationDate(command.getCalibrationDate());
        record.setResult(command.getResult());
        record.setCertificateNo(command.getCertificateNo());
        record.setRemark(command.getRemark());
        record.validateForCreate();
        metrologyRepository.insertRecord(record);
        if (record.passed()) {
            tool.calibrated(command.getCalibrationDate());
            metrologyRepository.updateTool(tool);
            if (plan != null) {
                plan.advance(command.getCalibrationDate());
                metrologyRepository.updatePlan(plan);
            }
        }
        return CalibrationRecordView.of(record);
    }

    /**
     * 填充量具可编辑字段。
     *
     * @param tool 量具
     * @param command 保存命令
     */
    private void applyTool(MeasuringTool tool, MeasuringToolSaveCommand command) {
        tool.setName(command.getName().trim());
        tool.setToolType(command.getToolType());
        tool.setPrecisionValue(command.getPrecisionValue());
        tool.setCalibrationCycleMonths(command.getCalibrationCycleMonths());
        tool.setLastCalibrationDate(command.getLastCalibrationDate());
        tool.setNextCalibrationDate(command.getNextCalibrationDate() != null ? command.getNextCalibrationDate()
                : command.getLastCalibrationDate() == null ? null
                : command.getLastCalibrationDate().plusMonths(command.getCalibrationCycleMonths()));
        tool.setStatus(command.getStatus() == null || command.getStatus().isBlank()
                ? QcStatus.ENABLED : command.getStatus());
        tool.setRemark(command.getRemark());
    }

    /**
     * 填充校准计划可编辑字段。
     *
     * @param plan 校准计划
     * @param command 保存命令
     */
    private void applyPlan(CalibrationPlan plan, CalibrationPlanSaveCommand command) {
        plan.setToolNo(command.getToolNo());
        plan.setCycleMonths(command.getCycleMonths());
        plan.setNextDate(command.getNextDate());
        plan.setStatus(command.getStatus() == null || command.getStatus().isBlank()
                ? QcStatus.ENABLED : command.getStatus());
        plan.setRemark(command.getRemark());
    }

    /**
     * 查询量具，不存在按 404 处理。
     *
     * @param toolNo 量具编号
     * @return 量具
     */
    private MeasuringTool requireTool(String toolNo) {
        return metrologyRepository.findToolByNo(toolNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.TOOL_NOT_FOUND,
                        "量具 " + toolNo + " 不存在或无权访问"));
    }

    /**
     * 查询校准计划，不存在按 404 处理。
     *
     * @param id 主键
     * @return 校准计划
     */
    private CalibrationPlan requirePlan(Long id) {
        return metrologyRepository.findPlanById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.CALIBRATION_PLAN_NOT_FOUND));
    }
}
