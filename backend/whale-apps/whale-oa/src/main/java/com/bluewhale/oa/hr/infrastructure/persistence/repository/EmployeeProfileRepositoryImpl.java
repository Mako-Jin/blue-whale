package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.model.EmploymentStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.EmployeeProfileEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.EmployeeProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 员工扩展档案仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EmployeeProfileRepositoryImpl implements EmployeeProfileRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final EmployeeProfileMapper employeeProfileMapper;

    @Override
    public List<EmployeeProfile> page(EmployeeProfileQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<EmployeeProfileEntity> page = employeeProfileMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(EmployeeProfileQuery query) {
        Long total = employeeProfileMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<EmployeeProfile> findById(Long id) {
        return Optional.ofNullable(employeeProfileMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<EmployeeProfile> findByUserId(Long userId) {
        return Optional.ofNullable(employeeProfileMapper.selectOne(new LambdaQueryWrapper<EmployeeProfileEntity>()
                .eq(EmployeeProfileEntity::getUserId, userId))).map(this::toDomain);
    }

    @Override
    public void insert(EmployeeProfile profile) {
        EmployeeProfileEntity entity = new EmployeeProfileEntity();
        fill(entity, profile);
        employeeProfileMapper.insert(entity);
        profile.setId(entity.getId());
        profile.setCreateTime(entity.getCreateTime());
        profile.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(EmployeeProfile profile) {
        employeeProfileMapper.update(null, new LambdaUpdateWrapper<EmployeeProfileEntity>()
                .eq(EmployeeProfileEntity::getId, profile.getId())
                .set(EmployeeProfileEntity::getUserId, profile.getUserId())
                .set(EmployeeProfileEntity::getEmployeeNo, profile.getEmployeeNo())
                .set(EmployeeProfileEntity::getDeptId, profile.getDeptId())
                .set(EmployeeProfileEntity::getHireDate, profile.getHireDate())
                .set(EmployeeProfileEntity::getRegularDate, profile.getRegularDate())
                .set(EmployeeProfileEntity::getBirthday, profile.getBirthday())
                .set(EmployeeProfileEntity::getEmploymentStatus,
                        profile.getEmploymentStatus() == null ? null : profile.getEmploymentStatus().name())
                .set(EmployeeProfileEntity::getLeaveDate, profile.getLeaveDate())
                .set(EmployeeProfileEntity::getCultureVisible, profile.getCultureVisible())
                .set(EmployeeProfileEntity::getRemark, profile.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        employeeProfileMapper.deleteById(id);
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<EmployeeProfileEntity> filter(EmployeeProfileQuery query) {
        return new LambdaQueryWrapper<EmployeeProfileEntity>()
                .eq(query.getUserId() != null, EmployeeProfileEntity::getUserId, query.getUserId())
                .like(StringUtils.hasText(query.getEmployeeNo()),
                        EmployeeProfileEntity::getEmployeeNo, query.getEmployeeNo())
                .eq(query.getDeptId() != null, EmployeeProfileEntity::getDeptId, query.getDeptId())
                .eq(StringUtils.hasText(query.getEmploymentStatus()),
                        EmployeeProfileEntity::getEmploymentStatus, query.getEmploymentStatus())
                .orderByDesc(EmployeeProfileEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private EmployeeProfile toDomain(EmployeeProfileEntity entity) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setId(entity.getId());
        profile.setUserId(entity.getUserId());
        profile.setEmployeeNo(entity.getEmployeeNo());
        profile.setDeptId(entity.getDeptId());
        profile.setHireDate(entity.getHireDate());
        profile.setRegularDate(entity.getRegularDate());
        profile.setBirthday(entity.getBirthday());
        profile.setEmploymentStatus(entity.getEmploymentStatus() == null
                ? null : EmploymentStatus.of(entity.getEmploymentStatus()));
        profile.setLeaveDate(entity.getLeaveDate());
        profile.setCultureVisible(entity.getCultureVisible());
        profile.setRemark(entity.getRemark());
        profile.setCreateTime(entity.getCreateTime());
        profile.setUpdateTime(entity.getUpdateTime());
        return profile;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param profile 领域模型
     */
    private void fill(EmployeeProfileEntity entity, EmployeeProfile profile) {
        entity.setUserId(profile.getUserId());
        entity.setEmployeeNo(profile.getEmployeeNo());
        entity.setDeptId(profile.getDeptId());
        entity.setHireDate(profile.getHireDate());
        entity.setRegularDate(profile.getRegularDate());
        entity.setBirthday(profile.getBirthday());
        entity.setEmploymentStatus(profile.getEmploymentStatus() == null
                ? EmploymentStatus.ACTIVE.name() : profile.getEmploymentStatus().name());
        entity.setLeaveDate(profile.getLeaveDate());
        entity.setCultureVisible(profile.getCultureVisible());
        entity.setRemark(profile.getRemark());
    }
}
