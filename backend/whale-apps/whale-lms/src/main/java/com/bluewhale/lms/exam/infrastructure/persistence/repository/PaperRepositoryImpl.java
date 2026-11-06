package com.bluewhale.lms.exam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuery;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.PaperStatus;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.PaperEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.PaperQuestionEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.PaperMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.PaperQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 试卷仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PaperRepositoryImpl implements PaperRepository {

    private final PaperMapper paperMapper;

    private final PaperQuestionMapper paperQuestionMapper;

    @Override
    public List<Paper> page(PaperQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<PaperEntity> page = paperMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(PaperQuery query) {
        return paperMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Paper> findById(Long id) {
        return Optional.ofNullable(paperMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Paper> findByCode(String paperCode) {
        return Optional.ofNullable(paperMapper.selectOne(new LambdaQueryWrapper<PaperEntity>()
                        .eq(PaperEntity::getPaperCode, paperCode)))
                .map(this::toDomain);
    }

    @Override
    public Long save(Paper paper) {
        PaperEntity entity = toEntity(paper);
        if (paper.getId() == null) {
            paperMapper.insert(entity);
        } else {
            paperMapper.updateById(entity);
        }
        paper.setId(entity.getId());
        paper.setCreateTime(entity.getCreateTime());
        paper.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        paperMapper.deleteById(id);
    }

    @Override
    public List<PaperQuestion> listQuestions(Long paperId) {
        return paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestionEntity>()
                        .eq(PaperQuestionEntity::getPaperId, paperId)
                        .orderByAsc(PaperQuestionEntity::getSortNo))
                .stream().map(this::toQuestionDomain).toList();
    }

    @Override
    public Long saveQuestion(PaperQuestion paperQuestion) {
        PaperQuestionEntity entity = toQuestionEntity(paperQuestion);
        if (paperQuestion.getId() == null) {
            paperQuestionMapper.insert(entity);
        } else {
            paperQuestionMapper.updateById(entity);
        }
        paperQuestion.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void deleteQuestionsBySource(Long paperId, String source) {
        paperQuestionMapper.deleteByPaperIdAndSource(paperId, source);
    }

    @Override
    public void deleteAllQuestions(Long paperId) {
        paperQuestionMapper.deleteByPaperId(paperId);
    }

    @Override
    public void deleteQuestion(Long paperId, Long questionId) {
        paperQuestionMapper.deleteByPaperIdAndQuestionId(paperId, questionId);
    }

    @Override
    public long countByQuestionId(Long questionId) {
        return paperQuestionMapper.selectCount(new LambdaQueryWrapper<PaperQuestionEntity>()
                .eq(PaperQuestionEntity::getQuestionId, questionId));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<PaperEntity> filter(PaperQuery query) {
        LambdaQueryWrapper<PaperEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PaperEntity::getTitle, keyword)
                    .or().like(PaperEntity::getPaperCode, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PaperEntity::getStatus, query.getStatus().trim());
        }
        if (query.getCourseId() != null) {
            wrapper.eq(PaperEntity::getCourseId, query.getCourseId());
        }
        wrapper.orderByDesc(PaperEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 试卷实体转领域。
     *
     * @param entity 实体
     * @return 试卷
     */
    private Paper toDomain(PaperEntity entity) {
        Paper paper = new Paper();
        paper.setId(entity.getId());
        paper.setPaperCode(entity.getPaperCode());
        paper.setTitle(entity.getTitle());
        paper.setCourseId(entity.getCourseId());
        paper.setDurationMinutes(entity.getDurationMinutes());
        paper.setTotalScore(entity.getTotalScore());
        paper.setPassScore(entity.getPassScore());
        paper.setQuestionCount(entity.getQuestionCount());
        paper.setSeed(entity.getSeed());
        paper.setRuleConfig(entity.getRuleConfig());
        paper.setStatus(PaperStatus.parse(entity.getStatus()));
        paper.setRemark(entity.getRemark());
        paper.setCreateTime(entity.getCreateTime());
        paper.setUpdateTime(entity.getUpdateTime());
        return paper;
    }

    /**
     * 试卷领域转实体。
     *
     * @param paper 试卷
     * @return 实体
     */
    private PaperEntity toEntity(Paper paper) {
        PaperEntity entity = new PaperEntity();
        entity.setId(paper.getId());
        entity.setPaperCode(paper.getPaperCode());
        entity.setTitle(paper.getTitle() == null ? null : paper.getTitle().trim());
        entity.setCourseId(paper.getCourseId());
        entity.setDurationMinutes(paper.getDurationMinutes());
        entity.setTotalScore(paper.getTotalScore());
        entity.setPassScore(paper.getPassScore());
        entity.setQuestionCount(paper.getQuestionCount());
        entity.setSeed(paper.getSeed());
        entity.setRuleConfig(paper.getRuleConfig());
        entity.setStatus(paper.getStatus() == null ? null : paper.getStatus().name());
        entity.setRemark(paper.getRemark());
        entity.setCreateTime(paper.getCreateTime());
        entity.setUpdateTime(paper.getUpdateTime());
        return entity;
    }

    /**
     * 试卷题目实体转领域。
     *
     * @param entity 实体
     * @return 试卷题目
     */
    private PaperQuestion toQuestionDomain(PaperQuestionEntity entity) {
        PaperQuestion paperQuestion = new PaperQuestion();
        paperQuestion.setId(entity.getId());
        paperQuestion.setPaperId(entity.getPaperId());
        paperQuestion.setQuestionId(entity.getQuestionId());
        paperQuestion.setScore(entity.getScore());
        paperQuestion.setSortNo(entity.getSortNo());
        paperQuestion.setSource(entity.getSource());
        return paperQuestion;
    }

    /**
     * 试卷题目领域转实体。
     *
     * @param paperQuestion 试卷题目
     * @return 实体
     */
    private PaperQuestionEntity toQuestionEntity(PaperQuestion paperQuestion) {
        PaperQuestionEntity entity = new PaperQuestionEntity();
        entity.setId(paperQuestion.getId());
        entity.setPaperId(paperQuestion.getPaperId());
        entity.setQuestionId(paperQuestion.getQuestionId());
        entity.setScore(paperQuestion.getScore());
        entity.setSortNo(paperQuestion.getSortNo());
        entity.setSource(paperQuestion.getSource());
        return entity;
    }
}
