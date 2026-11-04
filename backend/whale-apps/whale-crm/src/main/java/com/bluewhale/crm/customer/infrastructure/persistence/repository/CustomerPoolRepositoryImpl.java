package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerPoolRule;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerOwnerLogEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerPoolRuleEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerOwnerLogMapper;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerPoolRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 客户公海仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerPoolRepositoryImpl implements CustomerPoolRepository {

    private final CustomerPoolRuleMapper ruleMapper;

    private final CustomerOwnerLogMapper ownerLogMapper;

    @Override
    public Optional<CustomerPoolRule> findEnabled() {
        LambdaQueryWrapper<CustomerPoolRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerPoolRuleEntity::getEnabled, Boolean.TRUE)
                .orderByAsc(CustomerPoolRuleEntity::getId)
                .last("LIMIT 1");
        return Optional.ofNullable(ruleMapper.selectOne(wrapper)).map(this::toDomain);
    }

    @Override
    public Optional<CustomerPoolRule> findById(Long id) {
        return Optional.ofNullable(ruleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void saveRule(CustomerPoolRule rule) {
        CustomerPoolRuleEntity entity;
        if (rule.getId() == null) {
            LambdaQueryWrapper<CustomerPoolRuleEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CustomerPoolRuleEntity::getRuleName, rule.getRuleName()).last("LIMIT 1");
            CustomerPoolRuleEntity existing = ruleMapper.selectOne(wrapper);
            entity = existing == null ? new CustomerPoolRuleEntity() : existing;
        } else {
            entity = ruleMapper.selectById(rule.getId());
            if (entity == null) {
                entity = new CustomerPoolRuleEntity();
            }
        }
        entity.setRuleName(rule.getRuleName());
        entity.setInactiveDays(rule.getInactiveDays());
        entity.setNoOpportunityDays(rule.getNoOpportunityDays());
        entity.setLevels(rule.getLevels());
        entity.setDeptId(rule.getDeptId());
        entity.setDailyClaimLimit(rule.getDailyClaimLimit());
        entity.setEnabled(rule.getEnabled());
        entity.setLastRunAt(rule.getLastRunAt());
        entity.setRecycleCount(rule.getRecycleCount());
        if (entity.getId() == null) {
            ruleMapper.insert(entity);
        } else {
            ruleMapper.updateById(entity);
        }
        rule.setId(entity.getId());
    }

    @Override
    public long countClaims(Long operatorId, LocalDateTime since) {
        LambdaQueryWrapper<CustomerOwnerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerOwnerLogEntity::getOperatorId, operatorId)
                .eq(CustomerOwnerLogEntity::getAction, "CLAIM")
                .ge(CustomerOwnerLogEntity::getCreateTime, since);
        return ownerLogMapper.selectCount(wrapper);
    }

    @Override
    public void insertOwnerLog(CustomerOwnerLog log) {
        CustomerOwnerLogEntity entity = new CustomerOwnerLogEntity();
        entity.setCustomerId(log.getCustomerId());
        entity.setAction(log.getAction());
        entity.setFromOwnerId(log.getFromOwnerId());
        entity.setToOwnerId(log.getToOwnerId());
        entity.setOperatorId(log.getOperatorId());
        entity.setRemark(log.getRemark());
        ownerLogMapper.insert(entity);
        log.setId(entity.getId());
        log.setCreateTime(entity.getCreateTime());
    }

    @Override
    public List<CustomerOwnerLog> listOwnerLogs(Long customerId, int limit) {
        LambdaQueryWrapper<CustomerOwnerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerOwnerLogEntity::getCustomerId, customerId)
                .orderByDesc(CustomerOwnerLogEntity::getCreateTime)
                .last("LIMIT " + Math.max(1, limit));
        return ownerLogMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 规则实体
     * @return 领域模型
     */
    private CustomerPoolRule toDomain(CustomerPoolRuleEntity entity) {
        CustomerPoolRule rule = new CustomerPoolRule();
        rule.setId(entity.getId());
        rule.setRuleName(entity.getRuleName());
        rule.setInactiveDays(entity.getInactiveDays());
        rule.setNoOpportunityDays(entity.getNoOpportunityDays());
        rule.setLevels(entity.getLevels());
        rule.setDeptId(entity.getDeptId());
        rule.setDailyClaimLimit(entity.getDailyClaimLimit());
        rule.setEnabled(entity.getEnabled());
        rule.setLastRunAt(entity.getLastRunAt());
        rule.setRecycleCount(entity.getRecycleCount());
        return rule;
    }

    /**
     * 留痕实体转领域模型。
     *
     * @param entity 留痕实体
     * @return 领域模型
     */
    private CustomerOwnerLog toDomain(CustomerOwnerLogEntity entity) {
        CustomerOwnerLog log = new CustomerOwnerLog();
        log.setId(entity.getId());
        log.setCustomerId(entity.getCustomerId());
        log.setAction(entity.getAction());
        log.setFromOwnerId(entity.getFromOwnerId());
        log.setToOwnerId(entity.getToOwnerId());
        log.setOperatorId(entity.getOperatorId());
        log.setRemark(entity.getRemark());
        log.setCreateTime(entity.getCreateTime());
        return log;
    }
}
