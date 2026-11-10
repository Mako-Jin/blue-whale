package com.bluewhale.party.integration.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.party.integration.domain.model.EmployeeSnapshot;
import com.bluewhale.party.integration.domain.repository.EmployeeSnapshotRepository;
import com.bluewhale.party.integration.infrastructure.persistence.entity.EmployeeSnapshotEntity;
import com.bluewhale.party.integration.infrastructure.persistence.mapper.EmployeeSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 员工快照仓储实现（按员工编号 upsert）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EmployeeSnapshotRepositoryImpl implements EmployeeSnapshotRepository {

    private final EmployeeSnapshotMapper employeeSnapshotMapper;

    @Override
    public Optional<EmployeeSnapshot> findByEmployeeNo(String employeeNo) {
        return Optional.ofNullable(employeeSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<EmployeeSnapshotEntity>()
                                .eq(EmployeeSnapshotEntity::getEmployeeNo, employeeNo)
                                .last("LIMIT 1")))
                .map(this::toDomain);
    }

    @Override
    public Long save(EmployeeSnapshot snapshot) {
        EmployeeSnapshotEntity entity = toEntity(snapshot);
        EmployeeSnapshotEntity existing = snapshot.getId() == null
                ? employeeSnapshotMapper.selectOne(new LambdaQueryWrapper<EmployeeSnapshotEntity>()
                        .eq(EmployeeSnapshotEntity::getEmployeeNo, snapshot.getEmployeeNo())
                        .last("LIMIT 1"))
                : null;
        if (snapshot.getId() == null && existing != null) {
            entity.setId(existing.getId());
        }
        if (entity.getId() == null) {
            employeeSnapshotMapper.insert(entity);
        } else {
            employeeSnapshotMapper.updateById(entity);
        }
        snapshot.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public long countActive() {
        return employeeSnapshotMapper.selectCount(new LambdaQueryWrapper<EmployeeSnapshotEntity>()
                .eq(EmployeeSnapshotEntity::getStatus, "ACTIVE"));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private EmployeeSnapshot toDomain(EmployeeSnapshotEntity entity) {
        EmployeeSnapshot snapshot = new EmployeeSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setEmployeeNo(entity.getEmployeeNo());
        snapshot.setUserId(entity.getUserId());
        snapshot.setName(entity.getName());
        snapshot.setOrgCode(entity.getOrgCode());
        snapshot.setPosition(entity.getPosition());
        snapshot.setStatus(entity.getStatus());
        snapshot.setHireDate(entity.getHireDate());
        snapshot.setLeaveDate(entity.getLeaveDate());
        snapshot.setLastSyncedAt(entity.getLastSyncedAt());
        return snapshot;
    }

    /**
     * 领域模型转实体。
     *
     * @param snapshot 领域模型
     * @return 实体
     */
    private EmployeeSnapshotEntity toEntity(EmployeeSnapshot snapshot) {
        EmployeeSnapshotEntity entity = new EmployeeSnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setEmployeeNo(snapshot.getEmployeeNo());
        entity.setUserId(snapshot.getUserId());
        entity.setName(snapshot.getName());
        entity.setOrgCode(snapshot.getOrgCode());
        entity.setPosition(snapshot.getPosition());
        entity.setStatus(snapshot.getStatus());
        entity.setHireDate(snapshot.getHireDate());
        entity.setLeaveDate(snapshot.getLeaveDate());
        entity.setLastSyncedAt(snapshot.getLastSyncedAt());
        return entity;
    }
}
