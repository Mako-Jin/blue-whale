package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.OrgUnit;
import com.bluewhale.erp.hr.domain.repository.HrOrgRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.OrgUnitEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.OrgUnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class HrOrgRepositoryImpl implements HrOrgRepository {

    private final OrgUnitMapper orgUnitMapper;

    @Override
    public List<OrgUnit> list(HrQuery query) {
        return orgUnitMapper.selectList(filter(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<OrgUnit> findByCode(String code) {
        OrgUnitEntity entity = orgUnitMapper.selectOne(new LambdaQueryWrapper<OrgUnitEntity>()
                .eq(OrgUnitEntity::getCode, code)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void insert(OrgUnit org) {
        OrgUnitEntity entity = new OrgUnitEntity();
        fill(entity, org);
        orgUnitMapper.insert(entity);
        org.setId(entity.getId());
    }

    @Override
    public void update(OrgUnit org) {
        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setId(org.getId());
        entity.setName(org.getName());
        entity.setParentCode(org.getParentCode());
        entity.setManager(org.getManager());
        entity.setStatus(org.getStatus());
        entity.setRemark(org.getRemark());
        orgUnitMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<OrgUnitEntity> filter(HrQuery query) {
        LambdaQueryWrapper<OrgUnitEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(OrgUnitEntity::getCode, query.getKeyword())
                    .or().like(OrgUnitEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(OrgUnitEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(OrgUnitEntity::getCode);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OrgUnit toDomain(OrgUnitEntity entity) {
        OrgUnit org = new OrgUnit();
        org.setId(entity.getId());
        org.setCode(entity.getCode());
        org.setName(entity.getName());
        org.setParentCode(entity.getParentCode());
        org.setManager(entity.getManager());
        org.setStatus(entity.getStatus());
        org.setRemark(entity.getRemark());
        return org;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param org 领域模型
     */
    private void fill(OrgUnitEntity entity, OrgUnit org) {
        entity.setCode(org.getCode());
        entity.setName(org.getName());
        entity.setParentCode(org.getParentCode());
        entity.setManager(org.getManager());
        entity.setStatus(org.getStatus());
        entity.setRemark(org.getRemark());
    }
}
