package com.bluewhale.lms.training.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.lms.training.domain.model.AssignmentScope;
import com.bluewhale.lms.training.domain.model.TrainingAssignment;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.infrastructure.persistence.entity.TrainingAssignmentEntity;
import com.bluewhale.lms.training.infrastructure.persistence.mapper.TrainingAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 必修分配仓储实现（MyBatis-Plus）。
 *
 * <p>分配唯一性由表约束 {@code uk_lr_assignment}（tenant_id + class_id + user_id）
 * 与应用层幂等查询共同保证，因此批量分配可重复执行。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TrainingAssignmentRepositoryImpl implements TrainingAssignmentRepository {

    private final TrainingAssignmentMapper assignmentMapper;

    @Override
    public Optional<TrainingAssignment> findByClassAndUser(Long classId, Long userId) {
        return Optional.ofNullable(assignmentMapper.selectOne(new LambdaQueryWrapper<TrainingAssignmentEntity>()
                        .eq(TrainingAssignmentEntity::getClassId, classId)
                        .eq(TrainingAssignmentEntity::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(TrainingAssignment assignment) {
        TrainingAssignmentEntity entity = toEntity(assignment);
        assignmentMapper.insert(entity);
        assignment.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<Long> listUserIds(Long classId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<TrainingAssignmentEntity>()
                        .eq(TrainingAssignmentEntity::getClassId, classId))
                .stream().map(TrainingAssignmentEntity::getUserId).toList();
    }

    @Override
    public List<TrainingAssignment> listByClass(Long classId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<TrainingAssignmentEntity>()
                        .eq(TrainingAssignmentEntity::getClassId, classId)
                        .orderByAsc(TrainingAssignmentEntity::getAssignedAt))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countByClass(Long classId) {
        return assignmentMapper.selectCount(new LambdaQueryWrapper<TrainingAssignmentEntity>()
                .eq(TrainingAssignmentEntity::getClassId, classId));
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 分配记录
     */
    private TrainingAssignment toDomain(TrainingAssignmentEntity entity) {
        TrainingAssignment assignment = new TrainingAssignment();
        assignment.setId(entity.getId());
        assignment.setClassId(entity.getClassId());
        assignment.setScopeType(AssignmentScope.parse(entity.getScopeType()));
        assignment.setScopeValue(entity.getScopeValue());
        assignment.setUserId(entity.getUserId());
        assignment.setUsername(entity.getUsername());
        assignment.setAssignedAt(entity.getAssignedAt());
        return assignment;
    }

    /**
     * 领域转实体。
     *
     * @param assignment 分配记录
     * @return 实体
     */
    private TrainingAssignmentEntity toEntity(TrainingAssignment assignment) {
        TrainingAssignmentEntity entity = new TrainingAssignmentEntity();
        entity.setId(assignment.getId());
        entity.setClassId(assignment.getClassId());
        entity.setScopeType(assignment.getScopeType().name());
        entity.setScopeValue(assignment.getScopeValue());
        entity.setUserId(assignment.getUserId());
        entity.setUsername(assignment.getUsername());
        entity.setAssignedAt(assignment.getAssignedAt());
        return entity;
    }
}
