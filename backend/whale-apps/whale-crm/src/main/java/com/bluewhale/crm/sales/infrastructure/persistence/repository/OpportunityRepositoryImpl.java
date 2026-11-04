package com.bluewhale.crm.sales.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesOpportunityEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.SalesOpportunityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商机仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OpportunityRepositoryImpl implements OpportunityRepository {

    private final SalesOpportunityMapper opportunityMapper;

    @Override
    public List<Opportunity> page(OpportunityQuery query) {
        Page<SalesOpportunityEntity> page = opportunityMapper.selectPage(query.toPage(), buildWrapper(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(OpportunityQuery query) {
        return opportunityMapper.selectCount(buildWrapper(query));
    }

    @Override
    public List<Opportunity> list(OpportunityQuery query) {
        return opportunityMapper.selectList(buildWrapper(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Opportunity findById(Long id) {
        SalesOpportunityEntity entity = opportunityMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(Opportunity opportunity) {
        SalesOpportunityEntity entity = toEntity(opportunity);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        opportunityMapper.insert(entity);
        opportunity.setId(entity.getId());
        opportunity.setVersion(entity.getVersion());
        opportunity.setCreateTime(entity.getCreateTime());
    }

    @Override
    public boolean update(Opportunity opportunity) {
        SalesOpportunityEntity entity = toEntity(opportunity);
        entity.setId(opportunity.getId());
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        return opportunityMapper.updateById(entity) > 0;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SalesOpportunityEntity> buildWrapper(OpportunityQuery query) {
        LambdaQueryWrapper<SalesOpportunityEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SalesOpportunityEntity::getName, keyword.trim());
        }
        wrapper.eq(query.getCustomerId() != null, SalesOpportunityEntity::getCustomerId, query.getCustomerId());
        wrapper.eq(!isBlank(query.getStage()), SalesOpportunityEntity::getStage, query.getStage());
        wrapper.eq(query.getOwnerId() != null, SalesOpportunityEntity::getOwnerId, query.getOwnerId());
        wrapper.ge(query.getExpectedCloseFrom() != null,
                SalesOpportunityEntity::getExpectedCloseDate, query.getExpectedCloseFrom());
        wrapper.le(query.getExpectedCloseTo() != null,
                SalesOpportunityEntity::getExpectedCloseDate, query.getExpectedCloseTo());
        wrapper.orderByDesc(SalesOpportunityEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 判断字符串是否为空。
     *
     * @param text 文本
     * @return 空白返回 true
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Opportunity toDomain(SalesOpportunityEntity entity) {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(entity.getId());
        opportunity.setCustomerId(entity.getCustomerId());
        opportunity.setName(entity.getName());
        opportunity.setAmount(entity.getAmount());
        OpportunityStage stage = OpportunityStage.from(entity.getStage());
        opportunity.setStage(stage);
        opportunity.setProbability(entity.getProbability() == null ? stage.defaultProbability() : entity.getProbability());
        opportunity.setSource(entity.getSource());
        opportunity.setCompetitor(entity.getCompetitor());
        opportunity.setNextActionAt(entity.getNextActionAt());
        opportunity.setStageEnteredAt(entity.getStageEnteredAt());
        opportunity.setExpectedCloseDate(entity.getExpectedCloseDate());
        opportunity.setOwnerId(entity.getOwnerId());
        opportunity.setDeptId(entity.getDeptId());
        opportunity.setLostReason(entity.getLostReason());
        opportunity.setClosedAt(entity.getClosedAt());
        opportunity.setVersion(entity.getVersion());
        opportunity.setCreateTime(entity.getCreateTime());
        opportunity.setUpdateTime(entity.getUpdateTime());
        return opportunity;
    }

    /**
     * 领域模型转实体。
     *
     * @param opportunity 领域模型
     * @return 实体
     */
    private SalesOpportunityEntity toEntity(Opportunity opportunity) {
        SalesOpportunityEntity entity = new SalesOpportunityEntity();
        entity.setCustomerId(opportunity.getCustomerId());
        entity.setName(opportunity.getName());
        entity.setAmount(opportunity.getAmount());
        entity.setStage(opportunity.getStage() == null ? null : opportunity.getStage().name());
        entity.setProbability(opportunity.getProbability());
        entity.setSource(opportunity.getSource());
        entity.setCompetitor(opportunity.getCompetitor());
        entity.setNextActionAt(opportunity.getNextActionAt());
        entity.setStageEnteredAt(opportunity.getStageEnteredAt());
        entity.setExpectedCloseDate(opportunity.getExpectedCloseDate());
        entity.setOwnerId(opportunity.getOwnerId());
        entity.setDeptId(opportunity.getDeptId());
        entity.setLostReason(opportunity.getLostReason());
        entity.setClosedAt(opportunity.getClosedAt());
        entity.setVersion(opportunity.getVersion());
        return entity;
    }
}
