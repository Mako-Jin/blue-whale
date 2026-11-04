package com.bluewhale.erp.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.ReportDefinition;
import com.bluewhale.erp.analytics.domain.model.ReportSchedule;
import com.bluewhale.erp.analytics.domain.repository.ReportRepository;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.ReportDefinitionEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.ReportScheduleEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.ReportDefinitionMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.ReportScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 报表定义与定时计划仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportDefinitionMapper reportDefinitionMapper;

    private final ReportScheduleMapper reportScheduleMapper;

    @Override
    public List<ReportDefinition> pageDefinitions(AnalyticsQuery query) {
        Page<ReportDefinitionEntity> page = reportDefinitionMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                definitionFilter(query));
        return page.getRecords().stream().map(this::toDefinition).toList();
    }

    @Override
    public long countDefinitions(AnalyticsQuery query) {
        return reportDefinitionMapper.selectCount(definitionFilter(query));
    }

    @Override
    public Optional<ReportDefinition> findDefinitionByNo(String reportNo) {
        ReportDefinitionEntity entity = reportDefinitionMapper.selectOne(
                new LambdaQueryWrapper<ReportDefinitionEntity>()
                        .eq(ReportDefinitionEntity::getReportNo, reportNo)
                        .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDefinition(entity));
    }

    @Override
    public void insertDefinition(ReportDefinition definition) {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        fillDefinition(entity, definition);
        reportDefinitionMapper.insert(entity);
        definition.setId(entity.getId());
    }

    @Override
    public void updateDefinition(ReportDefinition definition) {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setId(definition.getId());
        fillDefinition(entity, definition);
        reportDefinitionMapper.updateById(entity);
    }

    @Override
    public List<ReportSchedule> pageSchedules(AnalyticsQuery query) {
        Page<ReportScheduleEntity> page = reportScheduleMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                scheduleFilter(query));
        return page.getRecords().stream().map(this::toSchedule).toList();
    }

    @Override
    public long countSchedules(AnalyticsQuery query) {
        return reportScheduleMapper.selectCount(scheduleFilter(query));
    }

    @Override
    public Optional<ReportSchedule> findScheduleByNo(String scheduleNo) {
        ReportScheduleEntity entity = reportScheduleMapper.selectOne(
                new LambdaQueryWrapper<ReportScheduleEntity>()
                        .eq(ReportScheduleEntity::getScheduleNo, scheduleNo)
                        .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toSchedule(entity));
    }

    @Override
    public void insertSchedule(ReportSchedule schedule) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        fillSchedule(entity, schedule);
        reportScheduleMapper.insert(entity);
        schedule.setId(entity.getId());
    }

    @Override
    public void updateSchedule(ReportSchedule schedule) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setId(schedule.getId());
        fillSchedule(entity, schedule);
        reportScheduleMapper.updateById(entity);
    }

    /**
     * 构建报表定义查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ReportDefinitionEntity> definitionFilter(AnalyticsQuery query) {
        LambdaQueryWrapper<ReportDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ReportDefinitionEntity::getReportNo, query.getKeyword())
                    .or().like(ReportDefinitionEntity::getReportName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ReportDefinitionEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getDimensionType())) {
            wrapper.eq(ReportDefinitionEntity::getDimensionType, query.getDimensionType());
        }
        if (StringUtils.hasText(query.getMetricType())) {
            wrapper.eq(ReportDefinitionEntity::getMetricType, query.getMetricType());
        }
        return wrapper.orderByAsc(ReportDefinitionEntity::getReportNo);
    }

    /**
     * 构建定时计划查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ReportScheduleEntity> scheduleFilter(AnalyticsQuery query) {
        LambdaQueryWrapper<ReportScheduleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ReportScheduleEntity::getScheduleNo, query.getKeyword())
                    .or().like(ReportScheduleEntity::getScheduleName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ReportScheduleEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriodCode())) {
            wrapper.eq(ReportScheduleEntity::getReportNo, query.getPeriodCode());
        }
        return wrapper.orderByAsc(ReportScheduleEntity::getScheduleNo);
    }

    /**
     * 定义实体转领域模型。
     *
     * @param entity 实体
     * @return 定义
     */
    private ReportDefinition toDefinition(ReportDefinitionEntity entity) {
        ReportDefinition definition = new ReportDefinition();
        definition.setId(entity.getId());
        definition.setReportNo(entity.getReportNo());
        definition.setReportName(entity.getReportName());
        definition.setReportType(entity.getReportType());
        definition.setDimensionType(entity.getDimensionType());
        definition.setMetricType(entity.getMetricType());
        definition.setFilterJson(entity.getFilterJson());
        definition.setStatus(entity.getStatus());
        definition.setOwner(entity.getOwner());
        definition.setRemark(entity.getRemark());
        return definition;
    }

    /**
     * 计划实体转领域模型。
     *
     * @param entity 实体
     * @return 计划
     */
    private ReportSchedule toSchedule(ReportScheduleEntity entity) {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setId(entity.getId());
        schedule.setScheduleNo(entity.getScheduleNo());
        schedule.setReportNo(entity.getReportNo());
        schedule.setScheduleName(entity.getScheduleName());
        schedule.setCronExpression(entity.getCronExpression());
        schedule.setChannel(entity.getChannel());
        schedule.setRecipients(entity.getRecipients());
        schedule.setLastRunTime(entity.getLastRunTime());
        schedule.setNextRunTime(entity.getNextRunTime());
        schedule.setStatus(entity.getStatus());
        schedule.setRemark(entity.getRemark());
        return schedule;
    }

    /**
     * 定义写入实体。
     *
     * @param entity 实体
     * @param definition 定义
     */
    private void fillDefinition(ReportDefinitionEntity entity, ReportDefinition definition) {
        entity.setReportNo(definition.getReportNo());
        entity.setReportName(definition.getReportName());
        entity.setReportType(definition.getReportType());
        entity.setDimensionType(definition.getDimensionType());
        entity.setMetricType(definition.getMetricType());
        entity.setFilterJson(definition.getFilterJson());
        entity.setStatus(definition.getStatus());
        entity.setOwner(definition.getOwner());
        entity.setRemark(definition.getRemark());
    }

    /**
     * 计划写入实体。
     *
     * @param entity 实体
     * @param schedule 计划
     */
    private void fillSchedule(ReportScheduleEntity entity, ReportSchedule schedule) {
        entity.setScheduleNo(schedule.getScheduleNo());
        entity.setReportNo(schedule.getReportNo());
        entity.setScheduleName(schedule.getScheduleName());
        entity.setCronExpression(schedule.getCronExpression());
        entity.setChannel(schedule.getChannel());
        entity.setRecipients(schedule.getRecipients());
        entity.setLastRunTime(schedule.getLastRunTime());
        entity.setNextRunTime(schedule.getNextRunTime());
        entity.setStatus(schedule.getStatus());
        entity.setRemark(schedule.getRemark());
    }
}
