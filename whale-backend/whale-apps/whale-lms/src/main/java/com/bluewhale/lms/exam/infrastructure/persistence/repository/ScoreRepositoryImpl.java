package com.bluewhale.lms.exam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.repository.ScoreRepository;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.ExamScoreEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.ExamScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 成绩仓储实现（MyBatis-Plus）。
 *
 * <p>成绩按版本留存：重判不覆盖历史版本，{@link #findLatest} 取版本号最大的那条。
 * 查询学员成绩列表时按判分时间倒序，同一时刻以版本号兜底，保证分页稳定。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ScoreRepositoryImpl implements ScoreRepository {

    private final ExamScoreMapper scoreMapper;

    @Override
    public Long save(ExamScore score) {
        ExamScoreEntity entity = toEntity(score);
        if (score.getId() == null) {
            scoreMapper.insert(entity);
        } else {
            scoreMapper.updateById(entity);
        }
        score.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<ExamScore> listByAttempt(Long attemptId) {
        return scoreMapper.selectList(new LambdaQueryWrapper<ExamScoreEntity>()
                        .eq(ExamScoreEntity::getAttemptId, attemptId)
                        .orderByAsc(ExamScoreEntity::getVersionNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ExamScore> findLatest(Long attemptId) {
        List<ExamScoreEntity> records = scoreMapper.selectList(new LambdaQueryWrapper<ExamScoreEntity>()
                .eq(ExamScoreEntity::getAttemptId, attemptId)
                .orderByDesc(ExamScoreEntity::getVersionNo));
        return records.stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<ExamScore> pageByUser(Long userId, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<ExamScoreEntity> page = scoreMapper.selectPage(new Page<>(current, Math.max(size, 1)),
                new LambdaQueryWrapper<ExamScoreEntity>()
                        .eq(ExamScoreEntity::getUserId, userId)
                        .orderByDesc(ExamScoreEntity::getGradedAt)
                        .orderByDesc(ExamScoreEntity::getVersionNo));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUser(Long userId) {
        return scoreMapper.selectCount(new LambdaQueryWrapper<ExamScoreEntity>()
                .eq(ExamScoreEntity::getUserId, userId));
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 成绩
     */
    private ExamScore toDomain(ExamScoreEntity entity) {
        ExamScore score = new ExamScore();
        score.setId(entity.getId());
        score.setAttemptId(entity.getAttemptId());
        score.setExamId(entity.getExamId());
        score.setUserId(entity.getUserId());
        score.setVersionNo(entity.getVersionNo());
        score.setObjectiveScore(entity.getObjectiveScore());
        score.setSubjectiveScore(entity.getSubjectiveScore());
        score.setTotalScore(entity.getTotalScore());
        score.setPassed(entity.getPassed() != null && entity.getPassed() == 1);
        score.setGradedBy(entity.getGradedBy());
        score.setGradedAt(entity.getGradedAt());
        score.setRemark(entity.getRemark());
        return score;
    }

    /**
     * 领域转实体。
     *
     * @param score 成绩
     * @return 实体
     */
    private ExamScoreEntity toEntity(ExamScore score) {
        ExamScoreEntity entity = new ExamScoreEntity();
        entity.setId(score.getId());
        entity.setAttemptId(score.getAttemptId());
        entity.setExamId(score.getExamId());
        entity.setUserId(score.getUserId());
        entity.setVersionNo(score.getVersionNo());
        entity.setObjectiveScore(score.getObjectiveScore());
        entity.setSubjectiveScore(score.getSubjectiveScore());
        entity.setTotalScore(score.getTotalScore());
        entity.setPassed(score.isPassed() ? 1 : 0);
        entity.setGradedBy(score.getGradedBy());
        entity.setGradedAt(score.getGradedAt());
        entity.setRemark(score.getRemark());
        return entity;
    }
}
