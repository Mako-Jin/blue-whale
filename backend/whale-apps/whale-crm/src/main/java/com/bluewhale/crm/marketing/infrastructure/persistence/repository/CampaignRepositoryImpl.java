package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.marketing.domain.model.Campaign;
import com.bluewhale.crm.marketing.domain.model.CampaignQuery;
import com.bluewhale.crm.marketing.domain.model.CampaignStatus;
import com.bluewhale.crm.marketing.domain.model.CampaignType;
import com.bluewhale.crm.marketing.domain.repository.CampaignRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.CampaignEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.CampaignMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 营销活动仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CampaignRepositoryImpl implements CampaignRepository {

    private final CampaignMapper campaignMapper;

    @Override
    public List<Campaign> page(CampaignQuery query) {
        Page<CampaignEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return campaignMapper.selectPage(page, buildWrapper(query)).getRecords().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(CampaignQuery query) {
        return campaignMapper.selectCount(buildWrapper(query));
    }

    @Override
    public Campaign findById(Long id) {
        CampaignEntity entity = campaignMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(Campaign campaign) {
        CampaignEntity entity = toEntity(campaign);
        campaignMapper.insert(entity);
        campaign.setId(entity.getId());
        campaign.setCreateTime(entity.getCreateTime());
        campaign.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Campaign campaign) {
        campaignMapper.updateById(toEntity(campaign));
    }

    @Override
    public long countByCodePrefix(String prefix) {
        return campaignMapper.selectCount(new LambdaQueryWrapper<CampaignEntity>()
                .likeRight(CampaignEntity::getCampaignCode, prefix));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CampaignEntity> buildWrapper(CampaignQuery query) {
        LambdaQueryWrapper<CampaignEntity> wrapper = new LambdaQueryWrapper<CampaignEntity>()
                .eq(StringUtils.hasText(query.getStatus()), CampaignEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCampaignType()), CampaignEntity::getCampaignType,
                        query.getCampaignType())
                .eq(StringUtils.hasText(query.getChannel()), CampaignEntity::getChannel, query.getChannel())
                .eq(query.getOwnerId() != null, CampaignEntity::getOwnerId, query.getOwnerId())
                .eq(query.getDeptId() != null, CampaignEntity::getDeptId, query.getDeptId())
                .eq(query.getScopeUserId() != null, CampaignEntity::getOwnerId, query.getScopeUserId())
                .orderByDesc(CampaignEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CampaignEntity::getCampaignCode, keyword)
                    .or().like(CampaignEntity::getCampaignName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 活动
     */
    private Campaign toDomain(CampaignEntity entity) {
        Campaign campaign = new Campaign();
        campaign.setId(entity.getId());
        campaign.setTenantId(entity.getTenantId());
        campaign.setCampaignCode(entity.getCampaignCode());
        campaign.setCampaignName(entity.getCampaignName());
        campaign.setCampaignType(CampaignType.from(entity.getCampaignType()));
        campaign.setStatus(CampaignStatus.from(entity.getStatus()));
        campaign.setChannel(entity.getChannel());
        campaign.setBudget(entity.getBudget());
        campaign.setActualCost(entity.getActualCost());
        campaign.setPlannedStart(entity.getPlannedStart());
        campaign.setPlannedEnd(entity.getPlannedEnd());
        campaign.setAudienceId(entity.getAudienceId());
        campaign.setAudienceCount(entity.getAudienceCount());
        campaign.setAttributedLeadCount(entity.getAttributedLeadCount());
        campaign.setAttributedWonAmount(entity.getAttributedWonAmount());
        campaign.setRoi(entity.getRoi());
        campaign.setOwnerId(entity.getOwnerId());
        campaign.setDeptId(entity.getDeptId());
        campaign.setLockVersion(entity.getLockVersion());
        campaign.setCreateTime(entity.getCreateTime());
        campaign.setUpdateTime(entity.getUpdateTime());
        return campaign;
    }

    /**
     * 领域对象转实体。
     *
     * @param campaign 活动
     * @return 实体
     */
    private CampaignEntity toEntity(Campaign campaign) {
        CampaignEntity entity = new CampaignEntity();
        entity.setId(campaign.getId());
        entity.setTenantId(campaign.getTenantId());
        entity.setCampaignCode(campaign.getCampaignCode());
        entity.setCampaignName(campaign.getCampaignName());
        entity.setCampaignType(campaign.getCampaignType() == null ? null : campaign.getCampaignType().name());
        entity.setStatus(campaign.getStatus() == null ? null : campaign.getStatus().name());
        entity.setChannel(campaign.getChannel());
        entity.setBudget(campaign.getBudget());
        entity.setActualCost(campaign.getActualCost());
        entity.setPlannedStart(campaign.getPlannedStart());
        entity.setPlannedEnd(campaign.getPlannedEnd());
        entity.setAudienceId(campaign.getAudienceId());
        entity.setAudienceCount(campaign.getAudienceCount());
        entity.setAttributedLeadCount(campaign.getAttributedLeadCount());
        entity.setAttributedWonAmount(campaign.getAttributedWonAmount());
        entity.setRoi(campaign.getRoi());
        entity.setOwnerId(campaign.getOwnerId());
        entity.setDeptId(campaign.getDeptId());
        entity.setLockVersion(campaign.getLockVersion());
        return entity;
    }
}
