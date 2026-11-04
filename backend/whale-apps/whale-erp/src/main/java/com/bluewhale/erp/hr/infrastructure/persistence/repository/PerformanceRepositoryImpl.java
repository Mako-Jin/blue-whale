package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.hr.domain.model.Appraisal;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.Kpi;
import com.bluewhale.erp.hr.domain.model.PerformanceCycle;
import com.bluewhale.erp.hr.domain.repository.PerformanceRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.AppraisalEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.KpiEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.PerformanceCycleEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.AppraisalMapper;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.KpiMapper;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.PerformanceCycleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 绩效周期仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PerformanceRepositoryImpl implements PerformanceRepository {

    /** 周期编号前缀 */
    private static final String DOC_PREFIX = "PC-";

    private final PerformanceCycleMapper performanceCycleMapper;

    private final KpiMapper kpiMapper;

    private final AppraisalMapper appraisalMapper;

    @Override
    public List<PerformanceCycle> page(HrQuery query) {
        Page<PerformanceCycleEntity> page = performanceCycleMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(HrQuery query) {
        return performanceCycleMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PerformanceCycle> findById(Long id) {
        PerformanceCycleEntity entity = performanceCycleMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public String nextCycleNo(String period) {
        long sequence = performanceCycleMapper.selectCount(new LambdaQueryWrapper<PerformanceCycleEntity>()
                .eq(PerformanceCycleEntity::getPeriod, period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period.replace("-", ""), sequence);
    }

    @Override
    public void insert(PerformanceCycle cycle) {
        PerformanceCycleEntity entity = new PerformanceCycleEntity();
        fill(entity, cycle);
        performanceCycleMapper.insert(entity);
        cycle.setId(entity.getId());
    }

    @Override
    public void update(PerformanceCycle cycle) {
        PerformanceCycleEntity entity = new PerformanceCycleEntity();
        entity.setId(cycle.getId());
        entity.setStatus(cycle.getStatus());
        entity.setRemark(cycle.getRemark());
        performanceCycleMapper.updateById(entity);
    }

    @Override
    public void insertKpi(Kpi kpi) {
        KpiEntity entity = new KpiEntity();
        fillKpi(entity, kpi);
        kpiMapper.insert(entity);
        kpi.setId(entity.getId());
    }

    @Override
    public List<Kpi> listKpis(Long cycleId) {
        return kpiMapper.selectList(new LambdaQueryWrapper<KpiEntity>()
                        .eq(KpiEntity::getCycleId, cycleId)
                        .orderByAsc(KpiEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Kpi> findKpi(Long cycleId, Long kpiId) {
        KpiEntity entity = kpiMapper.selectOne(new LambdaQueryWrapper<KpiEntity>()
                .eq(KpiEntity::getCycleId, cycleId)
                .eq(KpiEntity::getId, kpiId)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void upsertAppraisal(Appraisal appraisal) {
        AppraisalEntity existing = appraisalMapper.selectOne(new LambdaQueryWrapper<AppraisalEntity>()
                .eq(AppraisalEntity::getCycleId, appraisal.getCycleId())
                .eq(AppraisalEntity::getEmployeeNo, appraisal.getEmployeeNo())
                .eq(AppraisalEntity::getKpiId, appraisal.getKpiId())
                .last("limit 1"));
        if (existing == null) {
            AppraisalEntity entity = new AppraisalEntity();
            fillAppraisal(entity, appraisal);
            appraisalMapper.insert(entity);
            appraisal.setId(entity.getId());
            return;
        }
        existing.setKpiName(appraisal.getKpiName());
        existing.setEmployeeName(appraisal.getEmployeeName());
        existing.setScore(appraisal.getScore());
        existing.setGrade(appraisal.getGrade());
        existing.setRemark(appraisal.getRemark());
        appraisalMapper.updateById(existing);
        appraisal.setId(existing.getId());
    }

    @Override
    public List<Appraisal> listAppraisals(Long cycleId) {
        return appraisalMapper.selectList(new LambdaQueryWrapper<AppraisalEntity>()
                        .eq(AppraisalEntity::getCycleId, cycleId)
                        .orderByAsc(AppraisalEntity::getEmployeeNo)
                        .orderByAsc(AppraisalEntity::getKpiId))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构建周期查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PerformanceCycleEntity> filter(HrQuery query) {
        LambdaQueryWrapper<PerformanceCycleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PerformanceCycleEntity::getCycleNo, query.getKeyword())
                    .or().like(PerformanceCycleEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PerformanceCycleEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(PerformanceCycleEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(PerformanceCycleEntity::getPeriod)
                .orderByDesc(PerformanceCycleEntity::getCycleNo);
    }

    /**
     * 周期实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PerformanceCycle toDomain(PerformanceCycleEntity entity) {
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId(entity.getId());
        cycle.setCycleNo(entity.getCycleNo());
        cycle.setName(entity.getName());
        cycle.setPeriod(entity.getPeriod());
        cycle.setStartDate(entity.getStartDate());
        cycle.setEndDate(entity.getEndDate());
        cycle.setStatus(entity.getStatus());
        cycle.setRemark(entity.getRemark());
        return cycle;
    }

    /**
     * 指标实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Kpi toDomain(KpiEntity entity) {
        Kpi kpi = new Kpi();
        kpi.setId(entity.getId());
        kpi.setCycleId(entity.getCycleId());
        kpi.setName(entity.getName());
        kpi.setWeight(entity.getWeight());
        kpi.setTargetValue(entity.getTargetValue());
        kpi.setUnit(entity.getUnit());
        kpi.setRemark(entity.getRemark());
        return kpi;
    }

    /**
     * 考核结果实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Appraisal toDomain(AppraisalEntity entity) {
        Appraisal appraisal = new Appraisal();
        appraisal.setId(entity.getId());
        appraisal.setCycleId(entity.getCycleId());
        appraisal.setKpiId(entity.getKpiId());
        appraisal.setKpiName(entity.getKpiName());
        appraisal.setEmployeeNo(entity.getEmployeeNo());
        appraisal.setEmployeeName(entity.getEmployeeName());
        appraisal.setScore(entity.getScore());
        appraisal.setGrade(entity.getGrade());
        appraisal.setRemark(entity.getRemark());
        return appraisal;
    }

    /**
     * 领域模型写入周期实体。
     *
     * @param entity 实体
     * @param cycle 周期
     */
    private void fill(PerformanceCycleEntity entity, PerformanceCycle cycle) {
        entity.setCycleNo(cycle.getCycleNo());
        entity.setName(cycle.getName());
        entity.setPeriod(cycle.getPeriod());
        entity.setStartDate(cycle.getStartDate());
        entity.setEndDate(cycle.getEndDate());
        entity.setStatus(cycle.getStatus());
        entity.setRemark(cycle.getRemark());
    }

    /**
     * 领域模型写入指标实体。
     *
     * @param entity 实体
     * @param kpi 指标
     */
    private void fillKpi(KpiEntity entity, Kpi kpi) {
        entity.setCycleId(kpi.getCycleId());
        entity.setName(kpi.getName());
        entity.setWeight(kpi.getWeight());
        entity.setTargetValue(kpi.getTargetValue());
        entity.setUnit(kpi.getUnit());
        entity.setRemark(kpi.getRemark());
    }

    /**
     * 领域模型写入考核结果实体。
     *
     * @param entity 实体
     * @param appraisal 考核结果
     */
    private void fillAppraisal(AppraisalEntity entity, Appraisal appraisal) {
        entity.setCycleId(appraisal.getCycleId());
        entity.setKpiId(appraisal.getKpiId());
        entity.setKpiName(appraisal.getKpiName());
        entity.setEmployeeNo(appraisal.getEmployeeNo());
        entity.setEmployeeName(appraisal.getEmployeeName());
        entity.setScore(appraisal.getScore());
        entity.setGrade(appraisal.getGrade());
        entity.setRemark(appraisal.getRemark());
    }
}
