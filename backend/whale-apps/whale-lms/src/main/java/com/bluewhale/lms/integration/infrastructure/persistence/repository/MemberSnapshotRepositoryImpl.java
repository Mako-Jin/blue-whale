package com.bluewhale.lms.integration.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberSnapshotQuery;
import com.bluewhale.lms.integration.domain.model.MemberStatus;
import com.bluewhale.lms.integration.domain.repository.MemberSnapshotRepository;
import com.bluewhale.lms.integration.infrastructure.persistence.entity.MemberSnapshotEntity;
import com.bluewhale.lms.integration.infrastructure.persistence.mapper.MemberSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 员工只读快照仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MemberSnapshotRepositoryImpl implements MemberSnapshotRepository {

    private final MemberSnapshotMapper memberSnapshotMapper;

    @Override
    public List<MemberSnapshot> page(MemberSnapshotQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<MemberSnapshotEntity> page = memberSnapshotMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MemberSnapshotQuery query) {
        return memberSnapshotMapper.selectCount(filter(query));
    }

    @Override
    public Optional<MemberSnapshot> findByEmployeeNo(String employeeNo) {
        return Optional.ofNullable(memberSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<MemberSnapshotEntity>()
                                .eq(MemberSnapshotEntity::getEmployeeNo, employeeNo)))
                .map(this::toDomain);
    }

    @Override
    public Optional<MemberSnapshot> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<MemberSnapshotEntity>()
                                .eq(MemberSnapshotEntity::getUserId, userId)
                                .orderByAsc(MemberSnapshotEntity::getId)
                                .last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public List<MemberSnapshot> listByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return memberSnapshotMapper.selectList(new LambdaQueryWrapper<MemberSnapshotEntity>()
                        .in(MemberSnapshotEntity::getUserId, userIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(MemberSnapshot snapshot) {
        MemberSnapshotEntity entity = toEntity(snapshot);
        if (snapshot.getId() == null) {
            memberSnapshotMapper.insert(entity);
        } else {
            memberSnapshotMapper.updateById(entity);
        }
        snapshot.setId(entity.getId());
        snapshot.setCreateTime(entity.getCreateTime());
        snapshot.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countAll() {
        return memberSnapshotMapper.selectCount(new LambdaQueryWrapper<>());
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<MemberSnapshotEntity> filter(MemberSnapshotQuery query) {
        LambdaQueryWrapper<MemberSnapshotEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(MemberSnapshotEntity::getEmployeeNo, keyword)
                    .or().like(MemberSnapshotEntity::getName, keyword));
        }
        if (StringUtils.hasText(query.getOrgCode())) {
            wrapper.eq(MemberSnapshotEntity::getOrgCode, query.getOrgCode().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MemberSnapshotEntity::getStatus, query.getStatus().trim().toUpperCase());
        }
        wrapper.orderByAsc(MemberSnapshotEntity::getEmployeeNo);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 快照
     */
    private MemberSnapshot toDomain(MemberSnapshotEntity entity) {
        MemberSnapshot snapshot = new MemberSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setEmployeeNo(entity.getEmployeeNo());
        snapshot.setUserId(entity.getUserId());
        snapshot.setName(entity.getName());
        snapshot.setOrgCode(entity.getOrgCode());
        snapshot.setPosition(entity.getPosition());
        snapshot.setStatus(MemberStatus.parse(entity.getStatus()));
        snapshot.setHireDate(entity.getHireDate());
        snapshot.setLeaveDate(entity.getLeaveDate());
        snapshot.setLastSyncedAt(entity.getLastSyncedAt());
        snapshot.setCreateTime(entity.getCreateTime());
        snapshot.setUpdateTime(entity.getUpdateTime());
        return snapshot;
    }

    /**
     * 领域转实体。
     *
     * @param snapshot 快照
     * @return 实体
     */
    private MemberSnapshotEntity toEntity(MemberSnapshot snapshot) {
        MemberSnapshotEntity entity = new MemberSnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setEmployeeNo(snapshot.getEmployeeNo());
        entity.setUserId(snapshot.getUserId());
        entity.setName(snapshot.getName());
        entity.setOrgCode(snapshot.getOrgCode());
        entity.setPosition(snapshot.getPosition());
        entity.setStatus(snapshot.getStatus() == null ? MemberStatus.ACTIVE.name() : snapshot.getStatus().name());
        entity.setHireDate(snapshot.getHireDate());
        entity.setLeaveDate(snapshot.getLeaveDate());
        entity.setLastSyncedAt(snapshot.getLastSyncedAt());
        entity.setCreateTime(snapshot.getCreateTime());
        entity.setUpdateTime(snapshot.getUpdateTime());
        return entity;
    }
}
