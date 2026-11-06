package com.bluewhale.lms.credit.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleQuery;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditRuleRepository;
import com.bluewhale.lms.credit.infrastructure.persistence.entity.CreditRuleEntity;
import com.bluewhale.lms.credit.infrastructure.persistence.mapper.CreditRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 学分规则仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CreditRuleRepositoryImpl implements CreditRuleRepository {

    private final CreditRuleMapper creditRuleMapper;

    @Override
    public List<CreditRule> page(CreditRuleQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CreditRuleEntity> page = creditRuleMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CreditRuleQuery query) {
        return creditRuleMapper.selectCount(filter(query));
    }

    @Override
    public Optional<CreditRule> findById(Long id) {
        return Optional.ofNullable(creditRuleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<CreditRule> findByCode(String ruleCode) {
        return Optional.ofNullable(creditRuleMapper.selectOne(new LambdaQueryWrapper<CreditRuleEntity>()
                        .eq(CreditRuleEntity::getRuleCode, ruleCode)))
                .map(this::toDomain);
    }

    @Override
    public Optional<CreditRule> findByMatch(CreditSource sourceType, Long matchId) {
        return Optional.ofNullable(creditRuleMapper.selectOne(new LambdaQueryWrapper<CreditRuleEntity>()
                        .eq(CreditRuleEntity::getSourceType, sourceType.name())
                        .eq(CreditRuleEntity::getMatchId, matchId)))
                .map(this::toDomain);
    }

    @Override
    public Optional<CreditRule> findEffective(CreditSource sourceType, Long sourceId) {
        Optional<CreditRule> exact = matchRule(sourceType, sourceId);
        if (exact.isPresent()) {
            return exact;
        }
        return matchRule(sourceType, CreditRule.MATCH_ALL);
    }

    /**
     * 按来源与匹配对象取唯一规则（表上 uk_lr_credit_rule_match 保证至多一条）。
     *
     * @param sourceType 来源类型
     * @param matchId 匹配对象 ID
     * @return 规则
     */
    private Optional<CreditRule> matchRule(CreditSource sourceType, Long matchId) {
        return Optional.ofNullable(creditRuleMapper.selectOne(new LambdaQueryWrapper<CreditRuleEntity>()
                        .eq(CreditRuleEntity::getSourceType, sourceType.name())
                        .eq(CreditRuleEntity::getMatchId, matchId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(CreditRule rule) {
        CreditRuleEntity entity = toEntity(rule);
        if (rule.getId() == null) {
            creditRuleMapper.insert(entity);
        } else {
            creditRuleMapper.updateById(entity);
        }
        rule.setId(entity.getId());
        rule.setCreateTime(entity.getCreateTime());
        rule.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        creditRuleMapper.deleteById(id);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<CreditRuleEntity> filter(CreditRuleQuery query) {
        LambdaQueryWrapper<CreditRuleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CreditRuleEntity::getRuleCode, keyword)
                    .or().like(CreditRuleEntity::getName, keyword));
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(CreditRuleEntity::getSourceType, query.getSourceType().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CreditRuleEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(CreditRuleEntity::getUpdateTime);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 规则
     */
    private CreditRule toDomain(CreditRuleEntity entity) {
        CreditRule rule = new CreditRule();
        rule.setId(entity.getId());
        rule.setRuleCode(entity.getRuleCode());
        rule.setName(entity.getName());
        rule.setSourceType(CreditSource.parse(entity.getSourceType()));
        rule.setMatchId(entity.getMatchId() == null ? CreditRule.MATCH_ALL : entity.getMatchId());
        rule.setCredit(entity.getCredit());
        rule.setStatus(CreditRuleStatus.parse(entity.getStatus()));
        rule.setRemark(entity.getRemark());
        rule.setCreateTime(entity.getCreateTime());
        rule.setUpdateTime(entity.getUpdateTime());
        return rule;
    }

    /**
     * 领域转实体。
     *
     * @param rule 规则
     * @return 实体
     */
    private CreditRuleEntity toEntity(CreditRule rule) {
        CreditRuleEntity entity = new CreditRuleEntity();
        entity.setId(rule.getId());
        entity.setRuleCode(rule.getRuleCode());
        entity.setName(rule.getName() == null ? null : rule.getName().trim());
        entity.setSourceType(rule.getSourceType() == null ? null : rule.getSourceType().name());
        entity.setMatchId(rule.getMatchId() == null ? CreditRule.MATCH_ALL : rule.getMatchId());
        entity.setCredit(rule.getCredit());
        entity.setStatus(rule.getStatus() == null ? null : rule.getStatus().name());
        entity.setRemark(rule.getRemark());
        entity.setCreateTime(rule.getCreateTime());
        entity.setUpdateTime(rule.getUpdateTime());
        return entity;
    }
}
