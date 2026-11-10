package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.marketing.domain.model.CampaignMember;
import com.bluewhale.crm.marketing.domain.repository.CampaignMemberRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.CampaignMemberEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.CampaignMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 活动人群成员仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CampaignMemberRepositoryImpl implements CampaignMemberRepository {

    private final CampaignMemberMapper campaignMemberMapper;

    @Override
    public void insertBatch(List<CampaignMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (CampaignMember member : members) {
            CampaignMemberEntity entity = toEntity(member);
            campaignMemberMapper.insert(entity);
            member.setId(entity.getId());
        }
    }

    @Override
    public List<CampaignMember> findByAudience(Long audienceId) {
        return campaignMemberMapper.selectList(new LambdaQueryWrapper<CampaignMemberEntity>()
                        .eq(CampaignMemberEntity::getAudienceId, audienceId)
                        .orderByAsc(CampaignMemberEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByAudience(Long audienceId) {
        campaignMemberMapper.update(null, new LambdaUpdateWrapper<CampaignMemberEntity>()
                .eq(CampaignMemberEntity::getAudienceId, audienceId)
                .set(CampaignMemberEntity::getDeleted, 1));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 成员
     */
    private CampaignMember toDomain(CampaignMemberEntity entity) {
        CampaignMember member = new CampaignMember();
        member.setId(entity.getId());
        member.setTenantId(entity.getTenantId());
        member.setAudienceId(entity.getAudienceId());
        member.setCustomerId(entity.getCustomerId());
        member.setContactId(entity.getContactId());
        member.setContactName(entity.getContactName());
        member.setEmailHash(entity.getEmailHash());
        member.setPhoneHash(entity.getPhoneHash());
        member.setExcluded(entity.getExcluded());
        member.setExcludeReason(entity.getExcludeReason());
        member.setCreateTime(entity.getCreateTime());
        member.setUpdateTime(entity.getUpdateTime());
        return member;
    }

    /**
     * 领域对象转实体。
     *
     * @param member 成员
     * @return 实体
     */
    private CampaignMemberEntity toEntity(CampaignMember member) {
        CampaignMemberEntity entity = new CampaignMemberEntity();
        entity.setId(member.getId());
        entity.setTenantId(member.getTenantId());
        entity.setAudienceId(member.getAudienceId());
        entity.setCustomerId(member.getCustomerId());
        entity.setContactId(member.getContactId());
        entity.setContactName(member.getContactName());
        entity.setEmailHash(member.getEmailHash());
        entity.setPhoneHash(member.getPhoneHash());
        entity.setExcluded(member.getExcluded());
        entity.setExcludeReason(member.getExcludeReason());
        return entity;
    }
}
