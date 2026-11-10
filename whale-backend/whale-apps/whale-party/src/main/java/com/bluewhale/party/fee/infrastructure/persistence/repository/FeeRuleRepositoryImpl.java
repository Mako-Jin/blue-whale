package com.bluewhale.party.fee.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.party.fee.domain.model.FeeRule;
import com.bluewhale.party.fee.domain.repository.FeeRuleRepository;
import com.bluewhale.party.fee.infrastructure.persistence.entity.FeeRuleEntity;
import com.bluewhale.party.fee.infrastructure.persistence.mapper.FeeRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 党费标准仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FeeRuleRepositoryImpl implements FeeRuleRepository {

    /** 启用状态值 */
    private static final String ACTIVE = "ACTIVE";

    private final FeeRuleMapper feeRuleMapper;

    @Override
    public List<FeeRule> listAll() {
        return feeRuleMapper.selectList(new LambdaQueryWrapper<FeeRuleEntity>()
                        .orderByAsc(FeeRuleEntity::getMinBase))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<FeeRule> listActive() {
        return feeRuleMapper.selectList(new LambdaQueryWrapper<FeeRuleEntity>()
                        .eq(FeeRuleEntity::getStatus, ACTIVE)
                        .orderByAsc(FeeRuleEntity::getMinBase))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<FeeRule> findById(Long id) {
        return Optional.ofNullable(feeRuleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(FeeRule rule) {
        FeeRuleEntity entity = toEntity(rule);
        if (entity.getId() == null) {
            feeRuleMapper.insert(entity);
        } else {
            feeRuleMapper.updateById(entity);
        }
        rule.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public long count() {
        return feeRuleMapper.selectCount(new LambdaQueryWrapper<>());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private FeeRule toDomain(FeeRuleEntity entity) {
        FeeRule rule = new FeeRule();
        rule.setId(entity.getId());
        rule.setRuleName(entity.getRuleName());
        rule.setMinBase(entity.getMinBase());
        rule.setMaxBase(entity.getMaxBase());
        rule.setRate(entity.getRate());
        rule.setStatus(entity.getStatus());
        rule.setSortNo(entity.getSortNo());
        rule.setRemark(entity.getRemark());
        return rule;
    }

    /**
     * 领域模型转实体。
     *
     * @param rule 领域模型
     * @return 实体
     */
    private FeeRuleEntity toEntity(FeeRule rule) {
        FeeRuleEntity entity = new FeeRuleEntity();
        entity.setId(rule.getId());
        entity.setRuleName(rule.getRuleName());
        entity.setMinBase(rule.getMinBase());
        entity.setMaxBase(rule.getMaxBase());
        entity.setRate(rule.getRate());
        entity.setStatus(rule.getStatus() == null ? ACTIVE : rule.getStatus());
        entity.setSortNo(rule.getSortNo() == null ? 0 : rule.getSortNo());
        entity.setRemark(rule.getRemark());
        return entity;
    }
}
