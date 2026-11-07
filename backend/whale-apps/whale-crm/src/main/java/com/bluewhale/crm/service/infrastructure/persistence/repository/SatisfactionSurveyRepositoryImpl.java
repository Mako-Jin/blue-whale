package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.service.domain.model.SatisfactionSurvey;
import com.bluewhale.crm.service.domain.model.SurveyQuery;
import com.bluewhale.crm.service.domain.model.SurveyStatus;
import com.bluewhale.crm.service.domain.repository.SatisfactionSurveyRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SatisfactionSurveyEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.SatisfactionSurveyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 满意度问卷仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SatisfactionSurveyRepositoryImpl implements SatisfactionSurveyRepository {

    private final SatisfactionSurveyMapper satisfactionSurveyMapper;

    @Override
    public List<SatisfactionSurvey> page(SurveyQuery query) {
        LambdaQueryWrapper<SatisfactionSurveyEntity> wrapper = wrapper(query)
                .orderByDesc(SatisfactionSurveyEntity::getSentAt)
                .orderByDesc(SatisfactionSurveyEntity::getId)
                .last("limit " + offset(query) + ", " + size(query));
        return satisfactionSurveyMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SurveyQuery query) {
        return satisfactionSurveyMapper.selectCount(wrapper(query));
    }

    @Override
    public SatisfactionSurvey findById(Long id) {
        SatisfactionSurveyEntity entity = satisfactionSurveyMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public SatisfactionSurvey findBySurveyNo(String surveyNo) {
        SatisfactionSurveyEntity entity = satisfactionSurveyMapper.selectOne(
                new LambdaQueryWrapper<SatisfactionSurveyEntity>()
                        .eq(SatisfactionSurveyEntity::getSurveyNo, surveyNo)
                        .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public SatisfactionSurvey findPendingByTicket(Long ticketId) {
        SatisfactionSurveyEntity entity = satisfactionSurveyMapper.selectOne(
                new LambdaQueryWrapper<SatisfactionSurveyEntity>()
                        .eq(SatisfactionSurveyEntity::getTicketId, ticketId)
                        .eq(SatisfactionSurveyEntity::getStatus, SurveyStatus.SENT.name())
                        .orderByDesc(SatisfactionSurveyEntity::getSentAt)
                        .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(SatisfactionSurvey survey) {
        SatisfactionSurveyEntity entity = toEntity(survey);
        satisfactionSurveyMapper.insert(entity);
        survey.setId(entity.getId());
    }

    @Override
    public void update(SatisfactionSurvey survey) {
        satisfactionSurveyMapper.updateById(toEntity(survey));
    }

    @Override
    public long countByPrefix(String prefix) {
        return satisfactionSurveyMapper.selectCount(new LambdaQueryWrapper<SatisfactionSurveyEntity>()
                .likeRight(SatisfactionSurveyEntity::getSurveyNo, prefix));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 条件包装器
     */
    private LambdaQueryWrapper<SatisfactionSurveyEntity> wrapper(SurveyQuery query) {
        SurveyQuery safe = query == null ? new SurveyQuery() : query;
        return new LambdaQueryWrapper<SatisfactionSurveyEntity>()
                .eq(safe.getTicketId() != null, SatisfactionSurveyEntity::getTicketId, safe.getTicketId())
                .eq(safe.getStatus() != null && !safe.getStatus().isBlank(),
                        SatisfactionSurveyEntity::getStatus, safe.getStatus());
    }

    /**
     * 分页偏移。
     *
     * @param query 查询条件
     * @return 偏移
     */
    private long offset(SurveyQuery query) {
        int current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        return (long) (current - 1) * size(query);
    }

    /**
     * 每页条数。
     *
     * @param query 查询条件
     * @return 条数
     */
    private int size(SurveyQuery query) {
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        return Math.min(size, 100);
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 问卷
     */
    private SatisfactionSurvey toDomain(SatisfactionSurveyEntity entity) {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        survey.setId(entity.getId());
        survey.setTenantId(entity.getTenantId());
        survey.setSurveyNo(entity.getSurveyNo());
        survey.setTicketId(entity.getTicketId());
        survey.setTokenHash(entity.getTokenHash());
        survey.setStatus(SurveyStatus.from(entity.getStatus()));
        survey.setSentAt(entity.getSentAt());
        survey.setExpiresAt(entity.getExpiresAt());
        survey.setUsedAt(entity.getUsedAt());
        return survey;
    }

    /**
     * 领域对象转实体。
     *
     * @param survey 问卷
     * @return 实体
     */
    private SatisfactionSurveyEntity toEntity(SatisfactionSurvey survey) {
        SatisfactionSurveyEntity entity = new SatisfactionSurveyEntity();
        entity.setId(survey.getId());
        entity.setTenantId(survey.getTenantId());
        entity.setSurveyNo(survey.getSurveyNo());
        entity.setTicketId(survey.getTicketId());
        entity.setTokenHash(survey.getTokenHash());
        entity.setStatus(survey.getStatus() == null ? null : survey.getStatus().name());
        entity.setSentAt(survey.getSentAt());
        entity.setExpiresAt(survey.getExpiresAt());
        entity.setUsedAt(survey.getUsedAt());
        return entity;
    }
}
