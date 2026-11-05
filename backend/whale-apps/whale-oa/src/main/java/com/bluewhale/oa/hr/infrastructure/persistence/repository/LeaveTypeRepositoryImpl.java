package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeQuery;
import com.bluewhale.oa.hr.domain.model.LeaveTypeUnit;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.LeaveTypeEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.LeaveTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 假期类型仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LeaveTypeRepositoryImpl implements LeaveTypeRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final LeaveTypeMapper leaveTypeMapper;

    @Override
    public List<LeaveType> page(LeaveTypeQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<LeaveTypeEntity> page = leaveTypeMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(LeaveTypeQuery query) {
        Long total = leaveTypeMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<LeaveType> findById(Long id) {
        return Optional.ofNullable(leaveTypeMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<LeaveType> findByCode(String code) {
        return Optional.ofNullable(leaveTypeMapper.selectOne(new LambdaQueryWrapper<LeaveTypeEntity>()
                .eq(LeaveTypeEntity::getCode, code))).map(this::toDomain);
    }

    @Override
    public List<LeaveType> listEnabled() {
        return leaveTypeMapper.selectList(new LambdaQueryWrapper<LeaveTypeEntity>()
                .eq(LeaveTypeEntity::getEnabled, Boolean.TRUE)
                .orderByAsc(LeaveTypeEntity::getSortNo)
                .orderByAsc(LeaveTypeEntity::getId)).stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(LeaveType leaveType) {
        LeaveTypeEntity entity = new LeaveTypeEntity();
        fill(entity, leaveType);
        leaveTypeMapper.insert(entity);
        leaveType.setId(entity.getId());
        leaveType.setCreateTime(entity.getCreateTime());
        leaveType.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(LeaveType leaveType) {
        leaveTypeMapper.update(null, new LambdaUpdateWrapper<LeaveTypeEntity>()
                .eq(LeaveTypeEntity::getId, leaveType.getId())
                .set(LeaveTypeEntity::getCode, leaveType.getCode())
                .set(LeaveTypeEntity::getName, leaveType.getName())
                .set(LeaveTypeEntity::getUnit, leaveType.getUnit() == null ? null : leaveType.getUnit().name())
                .set(LeaveTypeEntity::getAnnualQuota, leaveType.getAnnualQuota())
                .set(LeaveTypeEntity::getMinUnit, leaveType.getMinUnit())
                .set(LeaveTypeEntity::getRequiresAttachment, leaveType.getRequiresAttachment())
                .set(LeaveTypeEntity::getAllowNegative, leaveType.getAllowNegative())
                .set(LeaveTypeEntity::getEnabled, leaveType.getEnabled())
                .set(LeaveTypeEntity::getSortNo, leaveType.getSortNo())
                .set(LeaveTypeEntity::getRemark, leaveType.getRemark()));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<LeaveTypeEntity> filter(LeaveTypeQuery query) {
        return new LambdaQueryWrapper<LeaveTypeEntity>()
                .like(StringUtils.hasText(query.getCode()), LeaveTypeEntity::getCode, query.getCode())
                .like(StringUtils.hasText(query.getName()), LeaveTypeEntity::getName, query.getName())
                .eq(query.getEnabled() != null, LeaveTypeEntity::getEnabled, query.getEnabled())
                .orderByAsc(LeaveTypeEntity::getSortNo)
                .orderByAsc(LeaveTypeEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private LeaveType toDomain(LeaveTypeEntity entity) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(entity.getId());
        leaveType.setCode(entity.getCode());
        leaveType.setName(entity.getName());
        leaveType.setUnit(entity.getUnit() == null ? null : LeaveTypeUnit.of(entity.getUnit()));
        leaveType.setAnnualQuota(entity.getAnnualQuota());
        leaveType.setMinUnit(entity.getMinUnit());
        leaveType.setRequiresAttachment(entity.getRequiresAttachment());
        leaveType.setAllowNegative(entity.getAllowNegative());
        leaveType.setEnabled(entity.getEnabled());
        leaveType.setSortNo(entity.getSortNo());
        leaveType.setRemark(entity.getRemark());
        leaveType.setCreateTime(entity.getCreateTime());
        leaveType.setUpdateTime(entity.getUpdateTime());
        return leaveType;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param leaveType 领域模型
     */
    private void fill(LeaveTypeEntity entity, LeaveType leaveType) {
        entity.setCode(leaveType.getCode());
        entity.setName(leaveType.getName());
        entity.setUnit(leaveType.getUnit() == null ? LeaveTypeUnit.DAY.name() : leaveType.getUnit().name());
        entity.setAnnualQuota(leaveType.getAnnualQuota());
        entity.setMinUnit(leaveType.getMinUnit());
        entity.setRequiresAttachment(leaveType.getRequiresAttachment());
        entity.setAllowNegative(leaveType.getAllowNegative());
        entity.setEnabled(leaveType.getEnabled());
        entity.setSortNo(leaveType.getSortNo());
        entity.setRemark(leaveType.getRemark());
    }
}
