package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.crm.customer.domain.model.CustomerFollowUp;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerFollowUpEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerFollowUpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 跟进记录仓储实现（append-only；仅合并场景允许整体改挂客户归属）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerFollowUpRepositoryImpl implements CustomerFollowUpRepository {

    private final CustomerFollowUpMapper followUpMapper;

    @Override
    public List<CustomerFollowUp> page(Long customerId, PageQuery pageQuery) {
        Page<CustomerFollowUpEntity> page = followUpMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<CustomerFollowUpEntity>()
                        .eq(CustomerFollowUpEntity::getCustomerId, customerId)
                        .orderByDesc(CustomerFollowUpEntity::getCreateTime));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(Long customerId) {
        return followUpMapper.selectCount(new LambdaQueryWrapper<CustomerFollowUpEntity>()
                .eq(CustomerFollowUpEntity::getCustomerId, customerId));
    }

    @Override
    public List<CustomerFollowUp> listRecent(Long customerId, int limit) {
        return followUpMapper.selectList(new LambdaQueryWrapper<CustomerFollowUpEntity>()
                        .eq(CustomerFollowUpEntity::getCustomerId, customerId)
                        .orderByDesc(CustomerFollowUpEntity::getCreateTime)
                        .last("LIMIT " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(CustomerFollowUp followUp) {
        CustomerFollowUpEntity entity = new CustomerFollowUpEntity();
        entity.setCustomerId(followUp.getCustomerId());
        entity.setOpportunityId(followUp.getOpportunityId());
        entity.setChannel(followUp.getChannel());
        entity.setContent(followUp.getContent());
        entity.setResult(followUp.getResult());
        entity.setNextAction(followUp.getNextAction());
        entity.setNextActionAt(followUp.getNextActionAt());
        entity.setCreatedBy(followUp.getCreatedBy());
        followUpMapper.insert(entity);
        followUp.setId(entity.getId());
        followUp.setCreateTime(entity.getCreateTime());
    }

    @Override
    public int reassignCustomer(Long fromCustomerId, Long toCustomerId) {
        LambdaUpdateWrapper<CustomerFollowUpEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerFollowUpEntity::getCustomerId, fromCustomerId)
                .set(CustomerFollowUpEntity::getCustomerId, toCustomerId);
        return followUpMapper.update(null, wrapper);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CustomerFollowUp toDomain(CustomerFollowUpEntity entity) {
        CustomerFollowUp followUp = new CustomerFollowUp();
        followUp.setId(entity.getId());
        followUp.setCustomerId(entity.getCustomerId());
        followUp.setOpportunityId(entity.getOpportunityId());
        followUp.setChannel(entity.getChannel());
        followUp.setContent(entity.getContent());
        followUp.setResult(entity.getResult());
        followUp.setNextAction(entity.getNextAction());
        followUp.setNextActionAt(entity.getNextActionAt());
        followUp.setCreatedBy(entity.getCreatedBy());
        followUp.setCreateTime(entity.getCreateTime());
        return followUp;
    }
}
