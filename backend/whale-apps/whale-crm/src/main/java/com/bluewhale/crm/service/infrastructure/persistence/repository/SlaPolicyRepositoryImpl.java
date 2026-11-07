package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.service.domain.model.SlaPolicy;
import com.bluewhale.crm.service.domain.model.TicketPriority;
import com.bluewhale.crm.service.domain.repository.SlaPolicyRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SlaPolicyEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.SlaPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SLA 策略仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SlaPolicyRepositoryImpl implements SlaPolicyRepository {

    private final SlaPolicyMapper slaPolicyMapper;

    @Override
    public List<SlaPolicy> findAll() {
        return slaPolicyMapper.selectList(new LambdaQueryWrapper<SlaPolicyEntity>()
                        .orderByAsc(SlaPolicyEntity::getPriority))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public SlaPolicy findByPriority(String priority) {
        SlaPolicyEntity entity = slaPolicyMapper.selectOne(new LambdaQueryWrapper<SlaPolicyEntity>()
                .eq(SlaPolicyEntity::getPriority, priority)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void upsert(SlaPolicy policy) {
        SlaPolicyEntity existing = slaPolicyMapper.selectOne(new LambdaQueryWrapper<SlaPolicyEntity>()
                .eq(SlaPolicyEntity::getPriority, policy.getPriority().name())
                .last("limit 1"));
        SlaPolicyEntity entity = toEntity(policy);
        if (existing == null) {
            slaPolicyMapper.insert(entity);
            policy.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            slaPolicyMapper.updateById(entity);
            policy.setId(existing.getId());
        }
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 策略
     */
    private SlaPolicy toDomain(SlaPolicyEntity entity) {
        SlaPolicy policy = new SlaPolicy();
        policy.setId(entity.getId());
        policy.setTenantId(entity.getTenantId());
        policy.setPriority(TicketPriority.from(entity.getPriority()));
        policy.setResponseHours(entity.getResponseHours());
        policy.setResolveHours(entity.getResolveHours());
        policy.setEnabled(entity.getEnabled());
        return policy;
    }

    /**
     * 领域对象转实体。
     *
     * @param policy 策略
     * @return 实体
     */
    private SlaPolicyEntity toEntity(SlaPolicy policy) {
        SlaPolicyEntity entity = new SlaPolicyEntity();
        entity.setTenantId(policy.getTenantId());
        entity.setPriority(policy.getPriority() == null ? null : policy.getPriority().name());
        entity.setResponseHours(policy.getResponseHours());
        entity.setResolveHours(policy.getResolveHours());
        entity.setEnabled(policy.getEnabled());
        return entity;
    }
}
