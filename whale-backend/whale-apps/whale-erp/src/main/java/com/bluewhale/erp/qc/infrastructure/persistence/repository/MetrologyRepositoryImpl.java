package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.CalibrationPlan;
import com.bluewhale.erp.qc.domain.model.CalibrationRecord;
import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.MetrologyRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.CalibrationPlanEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.CalibrationRecordEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.MeasuringToolEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.CalibrationPlanMapper;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.CalibrationRecordMapper;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.MeasuringToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 计量器具、校准计划与校准记录仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MetrologyRepositoryImpl implements MetrologyRepository {

    /** 校准计划号前缀 */
    private static final String PLAN_PREFIX = "CALP-";

    /** 校准记录号前缀 */
    private static final String RECORD_PREFIX = "CALR-";

    /** 单号期间格式 */
    private static final DateTimeFormatter DOC_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final MeasuringToolMapper measuringToolMapper;

    private final CalibrationPlanMapper calibrationPlanMapper;

    private final CalibrationRecordMapper calibrationRecordMapper;

    @Override
    public List<MeasuringTool> pageTools(QcQuery query) {
        Page<MeasuringToolEntity> page = measuringToolMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), toolFilter(query));
        return page.getRecords().stream().map(this::toTool).toList();
    }

    @Override
    public long countTools(QcQuery query) {
        return measuringToolMapper.selectCount(toolFilter(query));
    }

    @Override
    public Optional<MeasuringTool> findToolByNo(String toolNo) {
        MeasuringToolEntity entity = measuringToolMapper.selectOne(new LambdaQueryWrapper<MeasuringToolEntity>()
                .eq(MeasuringToolEntity::getToolNo, toolNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toTool(entity));
    }

    @Override
    public void insertTool(MeasuringTool tool) {
        MeasuringToolEntity entity = new MeasuringToolEntity();
        fillTool(entity, tool);
        measuringToolMapper.insert(entity);
        tool.setId(entity.getId());
    }

    @Override
    public void updateTool(MeasuringTool tool) {
        MeasuringToolEntity entity = new MeasuringToolEntity();
        entity.setId(tool.getId());
        entity.setName(tool.getName());
        entity.setToolType(tool.getToolType());
        entity.setPrecisionValue(tool.getPrecisionValue());
        entity.setCalibrationCycleMonths(tool.getCalibrationCycleMonths());
        entity.setLastCalibrationDate(tool.getLastCalibrationDate());
        entity.setNextCalibrationDate(tool.getNextCalibrationDate());
        entity.setStatus(tool.getStatus());
        entity.setRemark(tool.getRemark());
        measuringToolMapper.updateById(entity);
    }

    @Override
    public List<CalibrationPlan> pagePlans(QcQuery query) {
        Page<CalibrationPlanEntity> page = calibrationPlanMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), planFilter(query));
        return page.getRecords().stream().map(this::toPlan).toList();
    }

    @Override
    public long countPlans(QcQuery query) {
        return calibrationPlanMapper.selectCount(planFilter(query));
    }

    @Override
    public Optional<CalibrationPlan> findPlanById(Long id) {
        CalibrationPlanEntity entity = calibrationPlanMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toPlan(entity));
    }

    @Override
    public String nextPlanNo(LocalDate date) {
        String period = date.format(DOC_PERIOD_FORMATTER);
        long sequence = calibrationPlanMapper.selectCount(new LambdaQueryWrapper<CalibrationPlanEntity>()
                .likeRight(CalibrationPlanEntity::getPlanNo, PLAN_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", PLAN_PREFIX, period, sequence);
    }

    @Override
    public void insertPlan(CalibrationPlan plan) {
        CalibrationPlanEntity entity = new CalibrationPlanEntity();
        fillPlan(entity, plan);
        calibrationPlanMapper.insert(entity);
        plan.setId(entity.getId());
    }

    @Override
    public void updatePlan(CalibrationPlan plan) {
        CalibrationPlanEntity entity = new CalibrationPlanEntity();
        entity.setId(plan.getId());
        entity.setToolNo(plan.getToolNo());
        entity.setCycleMonths(plan.getCycleMonths());
        entity.setNextDate(plan.getNextDate());
        entity.setStatus(plan.getStatus());
        entity.setRemark(plan.getRemark());
        calibrationPlanMapper.updateById(entity);
    }

    @Override
    public List<CalibrationRecord> pageRecords(QcQuery query) {
        Page<CalibrationRecordEntity> page = calibrationRecordMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), recordFilter(query));
        return page.getRecords().stream().map(this::toRecord).toList();
    }

    @Override
    public long countRecords(QcQuery query) {
        return calibrationRecordMapper.selectCount(recordFilter(query));
    }

    @Override
    public String nextRecordNo(LocalDate date) {
        String period = date.format(DOC_PERIOD_FORMATTER);
        long sequence = calibrationRecordMapper.selectCount(new LambdaQueryWrapper<CalibrationRecordEntity>()
                .likeRight(CalibrationRecordEntity::getRecordNo, RECORD_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", RECORD_PREFIX, period, sequence);
    }

    @Override
    public void insertRecord(CalibrationRecord record) {
        CalibrationRecordEntity entity = new CalibrationRecordEntity();
        entity.setRecordNo(record.getRecordNo());
        entity.setToolNo(record.getToolNo());
        entity.setPlanId(record.getPlanId());
        entity.setCalibrationDate(record.getCalibrationDate());
        entity.setResult(record.getResult());
        entity.setCertificateNo(record.getCertificateNo());
        entity.setRemark(record.getRemark());
        calibrationRecordMapper.insert(entity);
        record.setId(entity.getId());
    }

    /**
     * 构建量具查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MeasuringToolEntity> toolFilter(QcQuery query) {
        LambdaQueryWrapper<MeasuringToolEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MeasuringToolEntity::getToolNo, query.getKeyword())
                    .or().like(MeasuringToolEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getToolNo())) {
            wrapper.eq(MeasuringToolEntity::getToolNo, query.getToolNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MeasuringToolEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(MeasuringToolEntity::getToolNo);
    }

    /**
     * 构建校准计划查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<CalibrationPlanEntity> planFilter(QcQuery query) {
        LambdaQueryWrapper<CalibrationPlanEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(CalibrationPlanEntity::getPlanNo, query.getKeyword())
                    .or().like(CalibrationPlanEntity::getToolNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getToolNo())) {
            wrapper.eq(CalibrationPlanEntity::getToolNo, query.getToolNo());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CalibrationPlanEntity::getStatus, query.getStatus());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(CalibrationPlanEntity::getNextDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(CalibrationPlanEntity::getNextDate, query.getToDate());
        }
        return wrapper.orderByAsc(CalibrationPlanEntity::getNextDate);
    }

    /**
     * 构建校准记录查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<CalibrationRecordEntity> recordFilter(QcQuery query) {
        LambdaQueryWrapper<CalibrationRecordEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(CalibrationRecordEntity::getRecordNo, query.getKeyword())
                    .or().like(CalibrationRecordEntity::getToolNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getToolNo())) {
            wrapper.eq(CalibrationRecordEntity::getToolNo, query.getToolNo());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(CalibrationRecordEntity::getCalibrationDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(CalibrationRecordEntity::getCalibrationDate, query.getToDate());
        }
        return wrapper.orderByDesc(CalibrationRecordEntity::getCalibrationDate);
    }

    /**
     * 量具实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MeasuringTool toTool(MeasuringToolEntity entity) {
        MeasuringTool tool = new MeasuringTool();
        tool.setId(entity.getId());
        tool.setToolNo(entity.getToolNo());
        tool.setName(entity.getName());
        tool.setToolType(entity.getToolType());
        tool.setPrecisionValue(entity.getPrecisionValue());
        tool.setCalibrationCycleMonths(entity.getCalibrationCycleMonths());
        tool.setLastCalibrationDate(entity.getLastCalibrationDate());
        tool.setNextCalibrationDate(entity.getNextCalibrationDate());
        tool.setStatus(entity.getStatus());
        tool.setRemark(entity.getRemark());
        return tool;
    }

    /**
     * 校准计划实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CalibrationPlan toPlan(CalibrationPlanEntity entity) {
        CalibrationPlan plan = new CalibrationPlan();
        plan.setId(entity.getId());
        plan.setPlanNo(entity.getPlanNo());
        plan.setToolNo(entity.getToolNo());
        plan.setCycleMonths(entity.getCycleMonths());
        plan.setNextDate(entity.getNextDate());
        plan.setStatus(entity.getStatus());
        plan.setRemark(entity.getRemark());
        return plan;
    }

    /**
     * 校准记录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CalibrationRecord toRecord(CalibrationRecordEntity entity) {
        CalibrationRecord record = new CalibrationRecord();
        record.setId(entity.getId());
        record.setRecordNo(entity.getRecordNo());
        record.setToolNo(entity.getToolNo());
        record.setPlanId(entity.getPlanId());
        record.setCalibrationDate(entity.getCalibrationDate());
        record.setResult(entity.getResult());
        record.setCertificateNo(entity.getCertificateNo());
        record.setRemark(entity.getRemark());
        return record;
    }

    /**
     * 领域模型写入量具实体。
     *
     * @param entity 实体
     * @param tool 量具
     */
    private void fillTool(MeasuringToolEntity entity, MeasuringTool tool) {
        entity.setToolNo(tool.getToolNo());
        entity.setName(tool.getName());
        entity.setToolType(tool.getToolType());
        entity.setPrecisionValue(tool.getPrecisionValue());
        entity.setCalibrationCycleMonths(tool.getCalibrationCycleMonths());
        entity.setLastCalibrationDate(tool.getLastCalibrationDate());
        entity.setNextCalibrationDate(tool.getNextCalibrationDate());
        entity.setStatus(tool.getStatus());
        entity.setRemark(tool.getRemark());
    }

    /**
     * 领域模型写入校准计划实体。
     *
     * @param entity 实体
     * @param plan 校准计划
     */
    private void fillPlan(CalibrationPlanEntity entity, CalibrationPlan plan) {
        entity.setPlanNo(plan.getPlanNo());
        entity.setToolNo(plan.getToolNo());
        entity.setCycleMonths(plan.getCycleMonths());
        entity.setNextDate(plan.getNextDate());
        entity.setStatus(plan.getStatus());
        entity.setRemark(plan.getRemark());
    }
}
