package com.bluewhale.lms.exam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamQuery;
import com.bluewhale.lms.exam.domain.model.ExamStatus;
import com.bluewhale.lms.exam.domain.repository.ExamRepository;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.ExamEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.ExamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 考试仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ExamRepositoryImpl implements ExamRepository {

    private final ExamMapper examMapper;

    @Override
    public List<Exam> page(ExamQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<ExamEntity> page = examMapper.selectPage(new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ExamQuery query) {
        return examMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Exam> findById(Long id) {
        return Optional.ofNullable(examMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Exam> findByCode(String examCode) {
        return Optional.ofNullable(examMapper.selectOne(new LambdaQueryWrapper<ExamEntity>()
                        .eq(ExamEntity::getExamCode, examCode)))
                .map(this::toDomain);
    }

    @Override
    public Long save(Exam exam) {
        ExamEntity entity = toEntity(exam);
        if (exam.getId() == null) {
            examMapper.insert(entity);
        } else {
            examMapper.updateById(entity);
        }
        exam.setId(entity.getId());
        exam.setCreateTime(entity.getCreateTime());
        exam.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        examMapper.deleteById(id);
    }

    @Override
    public long countByPaperId(Long paperId) {
        return examMapper.selectCount(new LambdaQueryWrapper<ExamEntity>()
                .eq(ExamEntity::getPaperId, paperId));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<ExamEntity> filter(ExamQuery query) {
        LambdaQueryWrapper<ExamEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ExamEntity::getTitle, keyword)
                    .or().like(ExamEntity::getExamCode, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ExamEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(ExamEntity::getStartTime);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 考试
     */
    private Exam toDomain(ExamEntity entity) {
        Exam exam = new Exam();
        exam.setId(entity.getId());
        exam.setExamCode(entity.getExamCode());
        exam.setTitle(entity.getTitle());
        exam.setPaperId(entity.getPaperId());
        exam.setCourseId(entity.getCourseId());
        exam.setStartTime(entity.getStartTime());
        exam.setEndTime(entity.getEndTime());
        exam.setDurationMinutes(entity.getDurationMinutes());
        exam.setMaxAttempts(entity.getMaxAttempts());
        exam.setShuffle(entity.getShuffle() != null && entity.getShuffle() == 1);
        exam.setPassScore(entity.getPassScore());
        exam.setSwitchLimit(entity.getSwitchLimit());
        exam.setStatus(ExamStatus.parse(entity.getStatus()));
        exam.setRemark(entity.getRemark());
        exam.setCreateTime(entity.getCreateTime());
        exam.setUpdateTime(entity.getUpdateTime());
        return exam;
    }

    /**
     * 领域转实体。
     *
     * @param exam 考试
     * @return 实体
     */
    private ExamEntity toEntity(Exam exam) {
        ExamEntity entity = new ExamEntity();
        entity.setId(exam.getId());
        entity.setExamCode(exam.getExamCode());
        entity.setTitle(exam.getTitle() == null ? null : exam.getTitle().trim());
        entity.setPaperId(exam.getPaperId());
        entity.setCourseId(exam.getCourseId());
        entity.setStartTime(exam.getStartTime());
        entity.setEndTime(exam.getEndTime());
        entity.setDurationMinutes(exam.getDurationMinutes());
        entity.setMaxAttempts(exam.getMaxAttempts());
        entity.setShuffle(exam.isShuffle() ? 1 : 0);
        entity.setPassScore(exam.getPassScore());
        entity.setSwitchLimit(exam.getSwitchLimit());
        entity.setStatus(exam.getStatus() == null ? null : exam.getStatus().name());
        entity.setRemark(exam.getRemark());
        entity.setCreateTime(exam.getCreateTime());
        entity.setUpdateTime(exam.getUpdateTime());
        return entity;
    }
}
