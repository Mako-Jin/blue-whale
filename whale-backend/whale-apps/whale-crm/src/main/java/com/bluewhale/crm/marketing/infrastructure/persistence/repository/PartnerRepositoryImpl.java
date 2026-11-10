package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.marketing.domain.model.Partner;
import com.bluewhale.crm.marketing.domain.model.PartnerQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerStatus;
import com.bluewhale.crm.marketing.domain.model.PartnerType;
import com.bluewhale.crm.marketing.domain.repository.PartnerRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.PartnerEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.PartnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 渠道伙伴仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PartnerRepositoryImpl implements PartnerRepository {

    private final PartnerMapper partnerMapper;

    @Override
    public List<Partner> page(PartnerQuery query) {
        Page<PartnerEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return partnerMapper.selectPage(page, buildWrapper(query)).getRecords().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(PartnerQuery query) {
        return partnerMapper.selectCount(buildWrapper(query));
    }

    @Override
    public Partner findById(Long id) {
        PartnerEntity entity = partnerMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(Partner partner) {
        PartnerEntity entity = toEntity(partner);
        partnerMapper.insert(entity);
        partner.setId(entity.getId());
        partner.setCreateTime(entity.getCreateTime());
        partner.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Partner partner) {
        partnerMapper.updateById(toEntity(partner));
    }

    @Override
    public boolean existsByCode(String partnerCode, Long excludeId) {
        LambdaQueryWrapper<PartnerEntity> wrapper = new LambdaQueryWrapper<PartnerEntity>()
                .eq(PartnerEntity::getPartnerCode, partnerCode)
                .ne(excludeId != null, PartnerEntity::getId, excludeId);
        return partnerMapper.selectCount(wrapper) > 0;
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartnerEntity> buildWrapper(PartnerQuery query) {
        LambdaQueryWrapper<PartnerEntity> wrapper = new LambdaQueryWrapper<PartnerEntity>()
                .eq(StringUtils.hasText(query.getStatus()), PartnerEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPartnerType()), PartnerEntity::getPartnerType,
                        query.getPartnerType())
                .eq(StringUtils.hasText(query.getRegion()), PartnerEntity::getRegion, query.getRegion())
                .eq(query.getScopeUserId() != null, PartnerEntity::getOwnerId, query.getScopeUserId())
                .orderByDesc(PartnerEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PartnerEntity::getPartnerCode, keyword)
                    .or().like(PartnerEntity::getPartnerName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 伙伴
     */
    private Partner toDomain(PartnerEntity entity) {
        Partner partner = new Partner();
        partner.setId(entity.getId());
        partner.setTenantId(entity.getTenantId());
        partner.setPartnerCode(entity.getPartnerCode());
        partner.setPartnerName(entity.getPartnerName());
        partner.setPartnerType(PartnerType.from(entity.getPartnerType()));
        partner.setLevel(entity.getLevel());
        partner.setRegion(entity.getRegion());
        partner.setContactName(entity.getContactName());
        partner.setContactPhone(entity.getContactPhone());
        partner.setStatus(PartnerStatus.from(entity.getStatus()));
        partner.setOwnerId(entity.getOwnerId());
        partner.setDeptId(entity.getDeptId());
        partner.setLockVersion(entity.getLockVersion());
        partner.setCreateTime(entity.getCreateTime());
        partner.setUpdateTime(entity.getUpdateTime());
        return partner;
    }

    /**
     * 领域对象转实体。
     *
     * @param partner 伙伴
     * @return 实体
     */
    private PartnerEntity toEntity(Partner partner) {
        PartnerEntity entity = new PartnerEntity();
        entity.setId(partner.getId());
        entity.setTenantId(partner.getTenantId());
        entity.setPartnerCode(partner.getPartnerCode());
        entity.setPartnerName(partner.getPartnerName());
        entity.setPartnerType(partner.getPartnerType() == null ? null : partner.getPartnerType().name());
        entity.setLevel(partner.getLevel());
        entity.setRegion(partner.getRegion());
        entity.setContactName(partner.getContactName());
        entity.setContactPhone(partner.getContactPhone());
        entity.setStatus(partner.getStatus() == null ? null : partner.getStatus().name());
        entity.setOwnerId(partner.getOwnerId());
        entity.setDeptId(partner.getDeptId());
        entity.setLockVersion(partner.getLockVersion());
        return entity;
    }
}
