package com.bluewhale.lms.exam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.AttemptRepository;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.ExamAnswerEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.ExamAttemptEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.ExamAnswerMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.ExamAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 作答记录仓储实现（MyBatis-Plus）。
 *
 * <p>作答记录与作答明细同属一个聚合：记录随作答进度更新，明细一题一条按
 * {@code question_id} 唯一。明细的 {@code sort_no} 只影响呈现次序，
 * 查询与判分一律不依赖它。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttemptRepositoryImpl implements AttemptRepository {

    private final ExamAttemptMapper attemptMapper;

    private final ExamAnswerMapper answerMapper;

    @Override
    public Long save(ExamAttempt attempt) {
        ExamAttemptEntity entity = toEntity(attempt);
        if (attempt.getId() == null) {
            attemptMapper.insert(entity);
        } else {
            attemptMapper.updateById(entity);
        }
        attempt.setId(entity.getId());
        attempt.setCreateTime(entity.getCreateTime());
        attempt.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public Optional<ExamAttempt> findById(Long id) {
        return Optional.ofNullable(attemptMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ExamAttempt> findInProgress(Long examId, Long userId) {
        List<ExamAttemptEntity> records = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttemptEntity>()
                .eq(ExamAttemptEntity::getExamId, examId)
                .eq(ExamAttemptEntity::getUserId, userId)
                .eq(ExamAttemptEntity::getStatus, AttemptStatus.IN_PROGRESS.name())
                .orderByDesc(ExamAttemptEntity::getAttemptNo));
        return records.stream().findFirst().map(this::toDomain);
    }

    @Override
    public long countByExamAndUser(Long examId, Long userId) {
        return attemptMapper.selectCount(new LambdaQueryWrapper<ExamAttemptEntity>()
                .eq(ExamAttemptEntity::getExamId, examId)
                .eq(ExamAttemptEntity::getUserId, userId));
    }

    @Override
    public List<ExamAttempt> page(AttemptQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<ExamAttemptEntity> page = attemptMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(AttemptQuery query) {
        return attemptMapper.selectCount(filter(query));
    }

    @Override
    public List<ExamAnswer> listAnswers(Long attemptId) {
        return answerMapper.selectList(new LambdaQueryWrapper<ExamAnswerEntity>()
                        .eq(ExamAnswerEntity::getAttemptId, attemptId)
                        .orderByAsc(ExamAnswerEntity::getSortNo)
                        .orderByAsc(ExamAnswerEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ExamAnswer> findAnswer(Long attemptId, Long questionId) {
        return Optional.ofNullable(answerMapper.selectOne(new LambdaQueryWrapper<ExamAnswerEntity>()
                        .eq(ExamAnswerEntity::getAttemptId, attemptId)
                        .eq(ExamAnswerEntity::getQuestionId, questionId)))
                .map(this::toDomain);
    }

    @Override
    public Long saveAnswer(ExamAnswer answer) {
        ExamAnswerEntity entity = toEntity(answer);
        if (answer.getId() == null) {
            answerMapper.insert(entity);
        } else {
            answerMapper.updateById(entity);
        }
        answer.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public long countPendingGrade(Long attemptId) {
        return answerMapper.selectCount(new LambdaQueryWrapper<ExamAnswerEntity>()
                .eq(ExamAnswerEntity::getAttemptId, attemptId)
                .eq(ExamAnswerEntity::getGraded, 0));
    }

    @Override
    public long countDistinctTakers() {
        return attemptMapper.countDistinctTakers();
    }

    @Override
    public long countDistinctPassed() {
        return attemptMapper.countDistinctPassed();
    }

    /**
     * 组装作答记录查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<ExamAttemptEntity> filter(AttemptQuery query) {
        LambdaQueryWrapper<ExamAttemptEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getExamId() != null) {
            wrapper.eq(ExamAttemptEntity::getExamId, query.getExamId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(ExamAttemptEntity::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ExamAttemptEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(ExamAttemptEntity::getStartedAt).orderByAsc(ExamAttemptEntity::getId);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 作答记录
     */
    private ExamAttempt toDomain(ExamAttemptEntity entity) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(entity.getId());
        attempt.setExamId(entity.getExamId());
        attempt.setPaperId(entity.getPaperId());
        attempt.setUserId(entity.getUserId());
        attempt.setUsername(entity.getUsername());
        attempt.setAttemptNo(entity.getAttemptNo());
        attempt.setSeed(entity.getSeed());
        attempt.setStartedAt(entity.getStartedAt());
        attempt.setDeadlineAt(entity.getDeadlineAt());
        attempt.setSubmittedAt(entity.getSubmittedAt());
        attempt.setStatus(AttemptStatus.parse(entity.getStatus()));
        attempt.setObjectiveScore(entity.getObjectiveScore());
        attempt.setSubjectiveScore(entity.getSubjectiveScore());
        attempt.setTotalScore(entity.getTotalScore());
        attempt.setPassed(toBoolean(entity.getPassed()));
        attempt.setSwitchCount(entity.getSwitchCount());
        attempt.setCreateTime(entity.getCreateTime());
        attempt.setUpdateTime(entity.getUpdateTime());
        return attempt;
    }

    /**
     * 领域转作答记录实体。
     *
     * @param attempt 作答记录
     * @return 实体
     */
    private ExamAttemptEntity toEntity(ExamAttempt attempt) {
        ExamAttemptEntity entity = new ExamAttemptEntity();
        entity.setId(attempt.getId());
        entity.setExamId(attempt.getExamId());
        entity.setPaperId(attempt.getPaperId());
        entity.setUserId(attempt.getUserId());
        entity.setUsername(attempt.getUsername());
        entity.setAttemptNo(attempt.getAttemptNo());
        entity.setSeed(attempt.getSeed());
        entity.setStartedAt(attempt.getStartedAt());
        entity.setDeadlineAt(attempt.getDeadlineAt());
        entity.setSubmittedAt(attempt.getSubmittedAt());
        entity.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        entity.setObjectiveScore(attempt.getObjectiveScore());
        entity.setSubjectiveScore(attempt.getSubjectiveScore());
        entity.setTotalScore(attempt.getTotalScore());
        entity.setPassed(toInteger(attempt.getPassed()));
        entity.setSwitchCount(attempt.getSwitchCount());
        entity.setCreateTime(attempt.getCreateTime());
        entity.setUpdateTime(attempt.getUpdateTime());
        return entity;
    }

    /**
     * 实体转领域（作答明细）。
     *
     * @param entity 实体
     * @return 作答明细
     */
    private ExamAnswer toDomain(ExamAnswerEntity entity) {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(entity.getId());
        answer.setAttemptId(entity.getAttemptId());
        answer.setQuestionId(entity.getQuestionId());
        answer.setQuestionType(QuestionType.parse(entity.getQuestionType()));
        answer.setAnswer(entity.getAnswer());
        answer.setCorrect(toBoolean(entity.getCorrect()));
        answer.setScore(entity.getScore());
        answer.setGraded(entity.getGraded() != null && entity.getGraded() == 1);
        answer.setSortNo(entity.getSortNo());
        return answer;
    }

    /**
     * 领域转实体（作答明细）。
     *
     * @param answer 作答明细
     * @return 实体
     */
    private ExamAnswerEntity toEntity(ExamAnswer answer) {
        ExamAnswerEntity entity = new ExamAnswerEntity();
        entity.setId(answer.getId());
        entity.setAttemptId(answer.getAttemptId());
        entity.setQuestionId(answer.getQuestionId());
        entity.setQuestionType(answer.getQuestionType() == null ? null : answer.getQuestionType().name());
        entity.setAnswer(answer.getAnswer());
        entity.setCorrect(toInteger(answer.getCorrect()));
        entity.setScore(answer.getScore());
        entity.setGraded(answer.isGraded() ? 1 : 0);
        entity.setSortNo(answer.getSortNo());
        return entity;
    }

    /**
     * 0/1 转布尔。
     *
     * @param value 0 或 1
     * @return 布尔值，空值返回 null
     */
    private Boolean toBoolean(Integer value) {
        return value == null ? null : value == 1;
    }

    /**
     * 布尔转 0/1。
     *
     * @param value 布尔值
     * @return 0 或 1，空值返回 null
     */
    private Integer toInteger(Boolean value) {
        return value == null ? null : (value ? 1 : 0);
    }
}
