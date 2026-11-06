package com.bluewhale.lms.exam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.exam.domain.model.Difficulty;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionOption;
import com.bluewhale.lms.exam.domain.model.QuestionQuery;
import com.bluewhale.lms.exam.domain.model.QuestionStatus;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.QuestionEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.QuestionOptionEntity;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.QuestionMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.mapper.QuestionOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 题库仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class QuestionRepositoryImpl implements QuestionRepository {

    private final QuestionMapper questionMapper;

    private final QuestionOptionMapper optionMapper;

    @Override
    public List<Question> page(QuestionQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<QuestionEntity> page = questionMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        List<QuestionEntity> records = page.getRecords();
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(QuestionEntity::getId).toList();
        Map<Long, List<QuestionOptionEntity>> options = optionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOptionEntity>()
                                .in(QuestionOptionEntity::getQuestionId, ids)
                                .orderByAsc(QuestionOptionEntity::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        return records.stream()
                .map(entity -> toDomain(entity, options.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    public long count(QuestionQuery query) {
        return questionMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Question> findById(Long id) {
        QuestionEntity entity = questionMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(entity, listOptions(id)));
    }

    @Override
    public Optional<Question> findByCode(String questionCode) {
        QuestionEntity entity = questionMapper.selectOne(new LambdaQueryWrapper<QuestionEntity>()
                .eq(QuestionEntity::getQuestionCode, questionCode));
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(entity, listOptions(entity.getId())));
    }

    @Override
    public Long save(Question question) {
        QuestionEntity entity = toEntity(question);
        if (question.getId() == null) {
            questionMapper.insert(entity);
        } else {
            questionMapper.updateById(entity);
        }
        optionMapper.deleteByQuestionId(entity.getId());
        int sortNo = 1;
        for (QuestionOption option : question.getOptions()) {
            QuestionOptionEntity optionEntity = new QuestionOptionEntity();
            optionEntity.setQuestionId(entity.getId());
            optionEntity.setOptionKey(option.getOptionKey());
            optionEntity.setContent(option.getContent() == null ? null : option.getContent().trim());
            optionEntity.setIsCorrect(option.isCorrect() ? 1 : 0);
            optionEntity.setSortNo(option.getSortNo() == null ? sortNo : option.getSortNo());
            optionMapper.insert(optionEntity);
            sortNo++;
        }
        question.setId(entity.getId());
        question.setCreateTime(entity.getCreateTime());
        question.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        questionMapper.deleteById(id);
    }

    @Override
    public List<Question> listPickCandidates(String category, String type, String difficulty) {
        LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<QuestionEntity>()
                .eq(QuestionEntity::getStatus, QuestionStatus.ACTIVE.name());
        if (StringUtils.hasText(category)) {
            wrapper.eq(QuestionEntity::getCategory, category.trim());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(QuestionEntity::getQuestionType, type.trim());
        }
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(QuestionEntity::getDifficulty, difficulty.trim());
        }
        wrapper.orderByAsc(QuestionEntity::getId);
        return questionMapper.selectList(wrapper).stream()
                .map(entity -> toDomain(entity, List.of()))
                .toList();
    }

    /**
     * 查询某题的全部选项。
     *
     * @param questionId 题目 ID
     * @return 选项实体列表
     */
    private List<QuestionOptionEntity> listOptions(Long questionId) {
        return optionMapper.selectList(new LambdaQueryWrapper<QuestionOptionEntity>()
                .eq(QuestionOptionEntity::getQuestionId, questionId)
                .orderByAsc(QuestionOptionEntity::getSortNo));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<QuestionEntity> filter(QuestionQuery query) {
        LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(QuestionEntity::getStem, keyword)
                    .or().like(QuestionEntity::getQuestionCode, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(QuestionEntity::getCategory, query.getCategory().trim());
        }
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(QuestionEntity::getQuestionType, query.getType().trim());
        }
        if (StringUtils.hasText(query.getDifficulty())) {
            wrapper.eq(QuestionEntity::getDifficulty, query.getDifficulty().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(QuestionEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(QuestionEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 题目实体
     * @param options 选项实体列表
     * @return 题目
     */
    private Question toDomain(QuestionEntity entity, List<QuestionOptionEntity> options) {
        Question question = new Question();
        question.setId(entity.getId());
        question.setQuestionCode(entity.getQuestionCode());
        question.setCategory(entity.getCategory());
        question.setType(QuestionType.parse(entity.getQuestionType()));
        question.setDifficulty(Difficulty.parse(entity.getDifficulty()));
        question.setTags(splitTags(entity.getTags()));
        question.setStem(entity.getStem());
        question.setStandardAnswer(entity.getStandardAnswer());
        question.setAnalysis(entity.getAnalysis());
        question.setDefaultScore(entity.getDefaultScore());
        question.setStatus(QuestionStatus.parse(entity.getStatus()));
        question.setCreateTime(entity.getCreateTime());
        question.setUpdateTime(entity.getUpdateTime());
        List<QuestionOption> list = new ArrayList<>();
        for (QuestionOptionEntity option : options) {
            QuestionOption domain = new QuestionOption();
            domain.setId(option.getId());
            domain.setQuestionId(option.getQuestionId());
            domain.setOptionKey(option.getOptionKey());
            domain.setContent(option.getContent());
            domain.setCorrect(option.getIsCorrect() != null && option.getIsCorrect() == 1);
            domain.setSortNo(option.getSortNo());
            list.add(domain);
        }
        question.setOptions(list);
        return question;
    }

    /**
     * 领域转实体。
     *
     * @param question 题目
     * @return 实体
     */
    private QuestionEntity toEntity(Question question) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(question.getId());
        entity.setQuestionCode(question.getQuestionCode());
        entity.setCategory(question.getCategory() == null ? null : question.getCategory().trim());
        entity.setQuestionType(question.getType() == null ? null : question.getType().name());
        entity.setDifficulty(question.getDifficulty() == null ? null : question.getDifficulty().name());
        entity.setTags(joinTags(question.getTags()));
        entity.setStem(question.getStem() == null ? null : question.getStem().trim());
        entity.setStandardAnswer(question.getStandardAnswer());
        entity.setAnalysis(question.getAnalysis() == null ? null : question.getAnalysis().trim());
        entity.setDefaultScore(question.getDefaultScore());
        entity.setStatus(question.getStatus() == null ? null : question.getStatus().name());
        entity.setCreateTime(question.getCreateTime());
        entity.setUpdateTime(question.getUpdateTime());
        return entity;
    }

    /**
     * 拆分标签串。
     *
     * @param raw 标签串
     * @return 标签列表
     */
    private List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 拼接标签串。
     *
     * @param tags 标签列表
     * @return 标签串
     */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }
}
