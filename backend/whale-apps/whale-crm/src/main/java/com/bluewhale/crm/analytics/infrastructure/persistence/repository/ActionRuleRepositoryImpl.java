package com.bluewhale.crm.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.analytics.domain.model.ActionRuleType;
import com.bluewhale.crm.analytics.domain.repository.ActionRuleRepository;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.ActionRuleEntity;
import com.bluewhale.crm.analytics.infrastructure.persistence.mapper.ActionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 行动计划规则仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ActionRuleRepositoryImpl implements ActionRuleRepository {

    private final ActionRuleMapper actionRuleMapper;

    /**
     * 查询全部规则。
     *
     * @return 规则列表
     */
    @Override
    public List<ActionRule> findAll() {
        return actionRuleMapper.selectList(new LambdaQueryWrapper<ActionRuleEntity>()
                .orderByAsc(ActionRuleEntity::getRuleCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 查询启用规则。
     *
     * @return 启用规则列表
     */
    @Override
    public List<ActionRule> findEnabled() {
        return actionRuleMapper.selectList(new LambdaQueryWrapper<ActionRuleEntity>()
                .eq(ActionRuleEntity::getEnabled, Boolean.TRUE)
                .orderByAsc(ActionRuleEntity::getRuleCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 规则
     */
    @Override
    public ActionRule findById(Long id) {
        ActionRuleEntity entity = actionRuleMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 按规则编码查询。
     *
     * @param ruleCode 规则编码
     * @return 规则
     */
    @Override
    public ActionRule findByCode(String ruleCode) {
        List<ActionRuleEntity> entities = actionRuleMapper.selectList(new LambdaQueryWrapper<ActionRuleEntity>()
                .eq(ActionRuleEntity::getRuleCode, ruleCode)
                .last("LIMIT 1"));
        return entities.isEmpty() ? null : toDomain(entities.get(0));
    }

    /**
     * 新增规则。
     *
     * @param rule 规则
     */
    @Override
    public void insert(ActionRule rule) {
        ActionRuleEntity entity = toEntity(rule);
        actionRuleMapper.insert(entity);
        rule.setId(entity.getId());
    }

    /**
     * 更新规则。
     *
     * @param rule 规则
     */
    @Override
    public void update(ActionRule rule) {
        actionRuleMapper.updateById(toEntity(rule));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 规则
     */
    private ActionRule toDomain(ActionRuleEntity entity) {
        ActionRule rule = new ActionRule();
        rule.setId(entity.getId());
        rule.setTenantId(entity.getTenantId());
        rule.setRuleCode(entity.getRuleCode());
        rule.setRuleName(entity.getRuleName());
        rule.setRuleType(ActionRuleType.from(entity.getRuleType()));
        rule.setThresholdDays(entity.getThresholdDays());
        rule.setEnabled(entity.getEnabled());
        rule.setDescription(entity.getDescription());
        rule.setCreateTime(entity.getCreateTime());
        rule.setUpdateTime(entity.getUpdateTime());
        return rule;
    }

    /**
     * 领域对象转实体。
     *
     * @param rule 规则
     * @return 实体
     */
    private ActionRuleEntity toEntity(ActionRule rule) {
        ActionRuleEntity entity = new ActionRuleEntity();
        entity.setId(rule.getId());
        entity.setTenantId(rule.getTenantId());
        entity.setRuleCode(rule.getRuleCode());
        entity.setRuleName(rule.getRuleName());
        entity.setRuleType(rule.getRuleType() == null ? null : rule.getRuleType().name());
        entity.setThresholdDays(rule.getThresholdDays());
        entity.setEnabled(rule.getEnabled());
        entity.setDescription(rule.getDescription());
        return entity;
    }
}
