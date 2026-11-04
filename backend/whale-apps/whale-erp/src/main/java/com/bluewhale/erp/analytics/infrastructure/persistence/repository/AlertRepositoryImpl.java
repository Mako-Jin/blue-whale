package com.bluewhale.erp.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.analytics.domain.model.AlertEvent;
import com.bluewhale.erp.analytics.domain.model.AlertRule;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.repository.AlertRepository;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.AlertEventEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.entity.AlertRuleEntity;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.AlertEventMapper;
import com.bluewhale.erp.analytics.infrastructure.persistence.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 预警规则与事件仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AlertRepositoryImpl implements AlertRepository {

    /** 事件编号前缀 */
    private static final String EVENT_PREFIX = "AE-";

    private final AlertRuleMapper alertRuleMapper;

    private final AlertEventMapper alertEventMapper;

    @Override
    public List<AlertRule> pageRules(AnalyticsQuery query) {
        Page<AlertRuleEntity> page = alertRuleMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                ruleFilter(query));
        return page.getRecords().stream().map(this::toRule).toList();
    }

    @Override
    public long countRules(AnalyticsQuery query) {
        return alertRuleMapper.selectCount(ruleFilter(query));
    }

    @Override
    public List<AlertRule> listEnabledRules() {
        return alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRuleEntity>()
                .eq(AlertRuleEntity::getStatus, AnalyticsStatus.ENABLED)
                .orderByAsc(AlertRuleEntity::getRuleNo)).stream().map(this::toRule).toList();
    }

    @Override
    public Optional<AlertRule> findRuleByNo(String ruleNo) {
        AlertRuleEntity entity = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRuleEntity>()
                .eq(AlertRuleEntity::getRuleNo, ruleNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toRule(entity));
    }

    @Override
    public Optional<AlertRule> findRuleById(Long id) {
        AlertRuleEntity entity = alertRuleMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toRule(entity));
    }

    @Override
    public void insertRule(AlertRule rule) {
        AlertRuleEntity entity = new AlertRuleEntity();
        fillRule(entity, rule);
        alertRuleMapper.insert(entity);
        rule.setId(entity.getId());
    }

    @Override
    public void updateRule(AlertRule rule) {
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setId(rule.getId());
        fillRule(entity, rule);
        alertRuleMapper.updateById(entity);
    }

    @Override
    public List<AlertEvent> pageEvents(AnalyticsQuery query) {
        Page<AlertEventEntity> page = alertEventMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                eventFilter(query));
        return page.getRecords().stream().map(this::toEvent).toList();
    }

    @Override
    public long countEvents(AnalyticsQuery query) {
        return alertEventMapper.selectCount(eventFilter(query));
    }

    @Override
    public long countOpenEvents() {
        return alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEventEntity>()
                .ne(AlertEventEntity::getStatus, AnalyticsStatus.EVENT_CLOSED));
    }

    @Override
    public Optional<AlertEvent> findEventById(Long id) {
        AlertEventEntity entity = alertEventMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toEvent(entity));
    }

    @Override
    public boolean existsEvent(Long ruleId, String objectKey, String periodCode) {
        return alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEventEntity>()
                .eq(AlertEventEntity::getRuleId, ruleId)
                .eq(AlertEventEntity::getObjectKey, objectKey)
                .eq(AlertEventEntity::getPeriodCode, periodCode)) > 0;
    }

    @Override
    public String nextEventNo(LocalDate date) {
        String prefix = EVENT_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long sequence = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEventEntity>()
                .likeRight(AlertEventEntity::getEventNo, prefix)) + 1;
        return String.format("%s%04d", prefix, sequence);
    }

    @Override
    public void insertEvent(AlertEvent event) {
        AlertEventEntity entity = new AlertEventEntity();
        fillEvent(entity, event);
        alertEventMapper.insert(entity);
        event.setId(entity.getId());
    }

    @Override
    public void updateEvent(AlertEvent event) {
        AlertEventEntity entity = new AlertEventEntity();
        entity.setId(event.getId());
        fillEvent(entity, event);
        alertEventMapper.updateById(entity);
    }

    /**
     * 规则查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<AlertRuleEntity> ruleFilter(AnalyticsQuery query) {
        LambdaQueryWrapper<AlertRuleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(AlertRuleEntity::getRuleNo, query.getKeyword())
                    .or().like(AlertRuleEntity::getRuleName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getRuleType())) {
            wrapper.eq(AlertRuleEntity::getRuleType, query.getRuleType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AlertRuleEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(AlertRuleEntity::getRuleNo);
    }

    /**
     * 事件查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<AlertEventEntity> eventFilter(AnalyticsQuery query) {
        LambdaQueryWrapper<AlertEventEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(AlertEventEntity::getEventNo, query.getKeyword())
                    .or().like(AlertEventEntity::getObjectKey, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getRuleType())) {
            wrapper.eq(AlertEventEntity::getRuleType, query.getRuleType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AlertEventEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriodCode())) {
            wrapper.eq(AlertEventEntity::getPeriodCode, query.getPeriodCode());
        }
        return wrapper.orderByDesc(AlertEventEntity::getTriggeredAt);
    }

    /**
     * 规则写入实体。
     *
     * @param entity 实体
     * @param rule 规则
     */
    private void fillRule(AlertRuleEntity entity, AlertRule rule) {
        entity.setRuleNo(rule.getRuleNo());
        entity.setRuleName(rule.getRuleName());
        entity.setRuleType(rule.getRuleType());
        entity.setThresholdValue(rule.getThresholdValue());
        entity.setPeriodType(rule.getPeriodType());
        entity.setLevel(rule.getLevel());
        entity.setSuggestion(rule.getSuggestion());
        entity.setStatus(rule.getStatus());
        entity.setRemark(rule.getRemark());
    }

    /**
     * 事件写入实体。
     *
     * @param entity 实体
     * @param event 事件
     */
    private void fillEvent(AlertEventEntity entity, AlertEvent event) {
        entity.setEventNo(event.getEventNo());
        entity.setRuleId(event.getRuleId());
        entity.setRuleNo(event.getRuleNo());
        entity.setRuleType(event.getRuleType());
        entity.setObjectKey(event.getObjectKey());
        entity.setPeriodCode(event.getPeriodCode());
        entity.setMetricValue(event.getMetricValue());
        entity.setThresholdValue(event.getThresholdValue());
        entity.setLevel(event.getLevel());
        entity.setStatus(event.getStatus());
        entity.setSuggestion(event.getSuggestion());
        entity.setTriggeredAt(event.getTriggeredAt());
        entity.setAcknowledgedBy(event.getAcknowledgedBy());
        entity.setAcknowledgedAt(event.getAcknowledgedAt());
        entity.setClosedBy(event.getClosedBy());
        entity.setClosedAt(event.getClosedAt());
        entity.setRemark(event.getRemark());
    }

    /**
     * 规则实体转领域模型。
     *
     * @param entity 实体
     * @return 规则
     */
    private AlertRule toRule(AlertRuleEntity entity) {
        AlertRule rule = new AlertRule();
        rule.setId(entity.getId());
        rule.setRuleNo(entity.getRuleNo());
        rule.setRuleName(entity.getRuleName());
        rule.setRuleType(entity.getRuleType());
        rule.setThresholdValue(entity.getThresholdValue());
        rule.setPeriodType(entity.getPeriodType());
        rule.setLevel(entity.getLevel());
        rule.setSuggestion(entity.getSuggestion());
        rule.setStatus(entity.getStatus());
        rule.setRemark(entity.getRemark());
        return rule;
    }

    /**
     * 事件实体转领域模型。
     *
     * @param entity 实体
     * @return 事件
     */
    private AlertEvent toEvent(AlertEventEntity entity) {
        AlertEvent event = new AlertEvent();
        event.setId(entity.getId());
        event.setEventNo(entity.getEventNo());
        event.setRuleId(entity.getRuleId());
        event.setRuleNo(entity.getRuleNo());
        event.setRuleType(entity.getRuleType());
        event.setObjectKey(entity.getObjectKey());
        event.setPeriodCode(entity.getPeriodCode());
        event.setMetricValue(entity.getMetricValue());
        event.setThresholdValue(entity.getThresholdValue());
        event.setLevel(entity.getLevel());
        event.setStatus(entity.getStatus());
        event.setSuggestion(entity.getSuggestion());
        event.setTriggeredAt(entity.getTriggeredAt());
        event.setAcknowledgedBy(entity.getAcknowledgedBy());
        event.setAcknowledgedAt(entity.getAcknowledgedAt());
        event.setClosedBy(entity.getClosedBy());
        event.setClosedAt(entity.getClosedAt());
        event.setRemark(entity.getRemark());
        return event;
    }
}
