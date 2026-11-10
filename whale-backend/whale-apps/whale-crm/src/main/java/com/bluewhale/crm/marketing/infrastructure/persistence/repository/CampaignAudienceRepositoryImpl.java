package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.marketing.domain.model.CampaignAudience;
import com.bluewhale.crm.marketing.domain.repository.CampaignAudienceRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.CampaignAudienceEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.CampaignAudienceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 活动人群快照仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CampaignAudienceRepositoryImpl implements CampaignAudienceRepository {

    private final CampaignAudienceMapper campaignAudienceMapper;

    @Override
    public void insert(CampaignAudience audience) {
        CampaignAudienceEntity entity = toEntity(audience);
        campaignAudienceMapper.insert(entity);
        audience.setId(entity.getId());
        audience.setCreateTime(entity.getCreateTime());
        audience.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(CampaignAudience audience) {
        campaignAudienceMapper.updateById(toEntity(audience));
    }

    @Override
    public CampaignAudience findById(Long id) {
        CampaignAudienceEntity entity = campaignAudienceMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public CampaignAudience findLatestByCampaign(Long campaignId) {
        List<CampaignAudienceEntity> entities = campaignAudienceMapper.selectList(
                new LambdaQueryWrapper<CampaignAudienceEntity>()
                        .eq(CampaignAudienceEntity::getCampaignId, campaignId)
                        .orderByDesc(CampaignAudienceEntity::getCreateTime));
        return entities.isEmpty() ? null : toDomain(entities.get(0));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 人群快照
     */
    private CampaignAudience toDomain(CampaignAudienceEntity entity) {
        CampaignAudience audience = new CampaignAudience();
        audience.setId(entity.getId());
        audience.setTenantId(entity.getTenantId());
        audience.setCampaignId(entity.getCampaignId());
        audience.setSnapshotNo(entity.getSnapshotNo());
        audience.setCriteriaJson(entity.getCriteriaJson());
        audience.setMemberCount(entity.getMemberCount());
        audience.setLocked(entity.getLocked());
        audience.setLockedAt(entity.getLockedAt());
        audience.setCreateTime(entity.getCreateTime());
        audience.setUpdateTime(entity.getUpdateTime());
        return audience;
    }

    /**
     * 领域对象转实体。
     *
     * @param audience 人群快照
     * @return 实体
     */
    private CampaignAudienceEntity toEntity(CampaignAudience audience) {
        CampaignAudienceEntity entity = new CampaignAudienceEntity();
        entity.setId(audience.getId());
        entity.setTenantId(audience.getTenantId());
        entity.setCampaignId(audience.getCampaignId());
        entity.setSnapshotNo(audience.getSnapshotNo());
        entity.setCriteriaJson(audience.getCriteriaJson());
        entity.setMemberCount(audience.getMemberCount());
        entity.setLocked(audience.getLocked());
        entity.setLockedAt(audience.getLockedAt());
        return entity;
    }
}
