package com.bluewhale.lms.course.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import com.bluewhale.lms.course.domain.repository.ProgressRepository;
import com.bluewhale.lms.course.infrastructure.persistence.entity.ProgressEntity;
import com.bluewhale.lms.course.infrastructure.persistence.mapper.ProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 学习进度仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ProgressRepositoryImpl implements ProgressRepository {

    private final ProgressMapper progressMapper;

    @Override
    public Optional<LearningProgress> find(Long enrollmentId, Long chapterId) {
        return Optional.ofNullable(progressMapper.selectOne(new LambdaQueryWrapper<ProgressEntity>()
                        .eq(ProgressEntity::getEnrollmentId, enrollmentId)
                        .eq(ProgressEntity::getChapterId, chapterId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(LearningProgress progress) {
        ProgressEntity entity = toEntity(progress);
        if (progress.getId() == null) {
            progressMapper.insert(entity);
        } else {
            progressMapper.updateById(entity);
        }
        progress.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<LearningProgress> listByEnrollment(Long enrollmentId) {
        return progressMapper.selectList(new LambdaQueryWrapper<ProgressEntity>()
                        .eq(ProgressEntity::getEnrollmentId, enrollmentId))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 进度
     */
    private LearningProgress toDomain(ProgressEntity entity) {
        LearningProgress progress = new LearningProgress();
        progress.setId(entity.getId());
        progress.setEnrollmentId(entity.getEnrollmentId());
        progress.setChapterId(entity.getChapterId());
        progress.setPercent(entity.getPercent());
        progress.setLastLearnedAt(entity.getLastLearnedAt());
        return progress;
    }

    /**
     * 领域转实体。
     *
     * @param progress 进度
     * @return 实体
     */
    private ProgressEntity toEntity(LearningProgress progress) {
        ProgressEntity entity = new ProgressEntity();
        entity.setId(progress.getId());
        entity.setEnrollmentId(progress.getEnrollmentId());
        entity.setChapterId(progress.getChapterId());
        entity.setPercent(progress.getPercent());
        entity.setLastLearnedAt(progress.getLastLearnedAt());
        return entity;
    }
}
