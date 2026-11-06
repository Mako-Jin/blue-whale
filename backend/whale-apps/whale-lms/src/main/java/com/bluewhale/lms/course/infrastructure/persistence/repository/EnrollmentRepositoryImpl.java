package com.bluewhale.lms.course.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.repository.EnrollmentRepository;
import com.bluewhale.lms.course.infrastructure.persistence.entity.EnrollmentEntity;
import com.bluewhale.lms.course.infrastructure.persistence.mapper.EnrollmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 学习报名仓储实现（MyBatis-Plus）。
 *
 * <p>报名唯一性由表约束 {@code uk_lr_enrollment}（tenant_id + course_id + user_id）
 * 与应用层幂等查询共同保证。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EnrollmentRepositoryImpl implements EnrollmentRepository {

    private final EnrollmentMapper enrollmentMapper;

    @Override
    public Optional<Enrollment> findByCourseAndUser(Long courseId, Long userId) {
        return Optional.ofNullable(enrollmentMapper.selectOne(new LambdaQueryWrapper<EnrollmentEntity>()
                        .eq(EnrollmentEntity::getCourseId, courseId)
                        .eq(EnrollmentEntity::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public Optional<Enrollment> findById(Long id) {
        return Optional.ofNullable(enrollmentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(Enrollment enrollment) {
        EnrollmentEntity entity = toEntity(enrollment);
        if (enrollment.getId() == null) {
            enrollmentMapper.insert(entity);
        } else {
            enrollmentMapper.updateById(entity);
        }
        enrollment.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<Enrollment> pageByUser(Long userId, long offset, int size) {
        return enrollmentMapper.selectList(new LambdaQueryWrapper<EnrollmentEntity>()
                        .eq(EnrollmentEntity::getUserId, userId)
                        .orderByDesc(EnrollmentEntity::getEnrolledAt)
                        .last("LIMIT " + Math.max(offset, 0) + ", " + Math.max(size, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return enrollmentMapper.selectCount(new LambdaQueryWrapper<EnrollmentEntity>()
                .eq(EnrollmentEntity::getUserId, userId));
    }

    @Override
    public long countAll() {
        return enrollmentMapper.selectCount(null);
    }

    @Override
    public long countDistinctLearners() {
        return enrollmentMapper.countDistinctLearners();
    }

    @Override
    public long countDistinctCompletedLearners() {
        return enrollmentMapper.countDistinctCompletedLearners();
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 报名记录
     */
    private Enrollment toDomain(EnrollmentEntity entity) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(entity.getId());
        enrollment.setCourseId(entity.getCourseId());
        enrollment.setUserId(entity.getUserId());
        enrollment.setUsername(entity.getUsername());
        enrollment.setEnrolledAt(entity.getEnrolledAt());
        return enrollment;
    }

    /**
     * 领域转实体。
     *
     * @param enrollment 报名记录
     * @return 实体
     */
    private EnrollmentEntity toEntity(Enrollment enrollment) {
        EnrollmentEntity entity = new EnrollmentEntity();
        entity.setId(enrollment.getId());
        entity.setCourseId(enrollment.getCourseId());
        entity.setUserId(enrollment.getUserId());
        entity.setUsername(enrollment.getUsername());
        entity.setEnrolledAt(enrollment.getEnrolledAt());
        return entity;
    }
}
