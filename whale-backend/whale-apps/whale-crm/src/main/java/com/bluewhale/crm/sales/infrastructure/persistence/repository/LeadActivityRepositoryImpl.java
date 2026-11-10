package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.crm.sales.domain.model.LeadActivity;
import com.bluewhale.crm.sales.domain.repository.LeadActivityRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.LeadActivityEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.LeadActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 线索活动仓储实现（append-only）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeadActivityRepositoryImpl implements LeadActivityRepository {

    private final LeadActivityMapper activityMapper;

    @Override
    public List<LeadActivity> page(Long leadId, PageQuery pageQuery) {
        Page<LeadActivityEntity> page = activityMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<LeadActivityEntity>()
                        .eq(LeadActivityEntity::getLeadId, leadId)
                        .orderByDesc(LeadActivityEntity::getCreateTime));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(Long leadId) {
        return activityMapper.selectCount(new LambdaQueryWrapper<LeadActivityEntity>()
                .eq(LeadActivityEntity::getLeadId, leadId));
    }

    @Override
    public void insert(LeadActivity activity) {
        LeadActivityEntity entity = new LeadActivityEntity();
        entity.setLeadId(activity.getLeadId());
        entity.setChannel(activity.getChannel());
        entity.setContent(activity.getContent());
        entity.setResult(activity.getResult());
        entity.setNextActionAt(activity.getNextActionAt());
        entity.setCreatedBy(activity.getCreatedBy());
        activityMapper.insert(entity);
        activity.setId(entity.getId());
        activity.setCreateTime(entity.getCreateTime());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private LeadActivity toDomain(LeadActivityEntity entity) {
        LeadActivity activity = new LeadActivity();
        activity.setId(entity.getId());
        activity.setLeadId(entity.getLeadId());
        activity.setChannel(entity.getChannel());
        activity.setContent(entity.getContent());
        activity.setResult(entity.getResult());
        activity.setNextActionAt(entity.getNextActionAt());
        activity.setCreatedBy(entity.getCreatedBy());
        activity.setCreateTime(entity.getCreateTime());
        return activity;
    }
}
