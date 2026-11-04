package com.bluewhale.oa.approval.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.oa.approval.domain.model.ApprovalRouteRule;
import com.bluewhale.oa.approval.domain.repository.ApprovalRouteRuleRepository;
import com.bluewhale.oa.approval.infrastructure.persistence.entity.ApprovalRouteRuleEntity;
import com.bluewhale.oa.approval.infrastructure.persistence.mapper.ApprovalRouteRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审批人解析规则仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ApprovalRouteRuleRepositoryImpl implements ApprovalRouteRuleRepository {

    private final ApprovalRouteRuleMapper approvalRouteRuleMapper;

    @Override
    public List<ApprovalRouteRule> listByBusinessType(String businessType) {
        return approvalRouteRuleMapper.selectList(new LambdaQueryWrapper<ApprovalRouteRuleEntity>()
                        .eq(ApprovalRouteRuleEntity::getBusinessType, businessType)
                        .orderByAsc(ApprovalRouteRuleEntity::getSortNo)
                        .orderByAsc(ApprovalRouteRuleEntity::getNodeKey))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ApprovalRouteRule> findById(Long id) {
        return Optional.ofNullable(approvalRouteRuleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalRouteRule> findByBusinessAndNode(String businessType, String nodeKey) {
        return Optional.ofNullable(approvalRouteRuleMapper.selectOne(new LambdaQueryWrapper<ApprovalRouteRuleEntity>()
                .eq(ApprovalRouteRuleEntity::getBusinessType, businessType)
                .eq(ApprovalRouteRuleEntity::getNodeKey, nodeKey)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public void insert(ApprovalRouteRule rule) {
        ApprovalRouteRuleEntity entity = new ApprovalRouteRuleEntity();
        fill(entity, rule);
        approvalRouteRuleMapper.insert(entity);
        rule.setId(entity.getId());
        rule.setCreateTime(entity.getCreateTime());
    }

    @Override
    public void update(ApprovalRouteRule rule) {
        approvalRouteRuleMapper.update(null, new LambdaUpdateWrapper<ApprovalRouteRuleEntity>()
                .eq(ApprovalRouteRuleEntity::getId, rule.getId())
                .set(ApprovalRouteRuleEntity::getNodeName, rule.getNodeName())
                .set(ApprovalRouteRuleEntity::getResolverType, rule.getResolverType())
                .set(ApprovalRouteRuleEntity::getResolverValue, rule.getResolverValue())
                .set(ApprovalRouteRuleEntity::getFallbackResolverType, rule.getFallbackResolverType())
                .set(ApprovalRouteRuleEntity::getFallbackResolverValue, rule.getFallbackResolverValue())
                .set(ApprovalRouteRuleEntity::getSortNo, rule.getSortNo())
                .set(ApprovalRouteRuleEntity::getEnabled, rule.isEnabled())
                .set(ApprovalRouteRuleEntity::getRemark, rule.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        approvalRouteRuleMapper.deleteById(id);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ApprovalRouteRule toDomain(ApprovalRouteRuleEntity entity) {
        ApprovalRouteRule rule = new ApprovalRouteRule();
        rule.setId(entity.getId());
        rule.setBusinessType(entity.getBusinessType());
        rule.setNodeKey(entity.getNodeKey());
        rule.setNodeName(entity.getNodeName());
        rule.setResolverType(entity.getResolverType());
        rule.setResolverValue(entity.getResolverValue());
        rule.setFallbackResolverType(entity.getFallbackResolverType());
        rule.setFallbackResolverValue(entity.getFallbackResolverValue());
        rule.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        rule.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        rule.setRemark(entity.getRemark());
        rule.setCreateTime(entity.getCreateTime());
        rule.setUpdateTime(entity.getUpdateTime());
        return rule;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param rule 领域模型
     */
    private void fill(ApprovalRouteRuleEntity entity, ApprovalRouteRule rule) {
        entity.setBusinessType(rule.getBusinessType());
        entity.setNodeKey(rule.getNodeKey());
        entity.setNodeName(rule.getNodeName());
        entity.setResolverType(rule.getResolverType());
        entity.setResolverValue(rule.getResolverValue());
        entity.setFallbackResolverType(rule.getFallbackResolverType());
        entity.setFallbackResolverValue(rule.getFallbackResolverValue());
        entity.setSortNo(rule.getSortNo());
        entity.setEnabled(rule.isEnabled());
        entity.setRemark(rule.getRemark());
    }
}
