package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bluewhale.crm.service.domain.model.SatisfactionResponse;
import com.bluewhale.crm.service.domain.repository.SatisfactionResponseRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SatisfactionResponseEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.SatisfactionResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 满意度答卷仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SatisfactionResponseRepositoryImpl implements SatisfactionResponseRepository {

    private final SatisfactionResponseMapper satisfactionResponseMapper;

    @Override
    public void insert(SatisfactionResponse response) {
        SatisfactionResponseEntity entity = toEntity(response);
        satisfactionResponseMapper.insert(entity);
        response.setId(entity.getId());
    }

    @Override
    public List<SatisfactionResponse> findBySurveyIds(Collection<Long> surveyIds) {
        if (surveyIds == null || surveyIds.isEmpty()) {
            return List.of();
        }
        return satisfactionResponseMapper.selectList(new LambdaQueryWrapper<SatisfactionResponseEntity>()
                        .in(SatisfactionResponseEntity::getSurveyId, surveyIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countAll() {
        return satisfactionResponseMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public double averageScore() {
        Object value = satisfactionResponseMapper.selectObjs(new QueryWrapper<SatisfactionResponseEntity>()
                        .select("AVG(score)"))
                .stream().findFirst().orElse(null);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    @Override
    public long countLowScore() {
        return satisfactionResponseMapper.selectCount(new LambdaQueryWrapper<SatisfactionResponseEntity>()
                .eq(SatisfactionResponseEntity::getLowScore, true));
    }

    @Override
    public long countFollowUp() {
        return satisfactionResponseMapper.selectCount(new LambdaQueryWrapper<SatisfactionResponseEntity>()
                .eq(SatisfactionResponseEntity::getFollowUpRequired, true));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 答卷
     */
    private SatisfactionResponse toDomain(SatisfactionResponseEntity entity) {
        SatisfactionResponse response = new SatisfactionResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setSurveyId(entity.getSurveyId());
        response.setTicketId(entity.getTicketId());
        response.setScore(entity.getScore());
        response.setComment(entity.getComment());
        response.setSpeedScore(entity.getSpeedScore());
        response.setAttitudeScore(entity.getAttitudeScore());
        response.setProfessionalScore(entity.getProfessionalScore());
        response.setLowScore(entity.getLowScore());
        response.setFollowUpRequired(entity.getFollowUpRequired());
        response.setSubmittedAt(entity.getSubmittedAt());
        return response;
    }

    /**
     * 领域对象转实体。
     *
     * @param response 答卷
     * @return 实体
     */
    private SatisfactionResponseEntity toEntity(SatisfactionResponse response) {
        SatisfactionResponseEntity entity = new SatisfactionResponseEntity();
        entity.setId(response.getId());
        entity.setTenantId(response.getTenantId());
        entity.setSurveyId(response.getSurveyId());
        entity.setTicketId(response.getTicketId());
        entity.setScore(response.getScore());
        entity.setComment(response.getComment());
        entity.setSpeedScore(response.getSpeedScore());
        entity.setAttitudeScore(response.getAttitudeScore());
        entity.setProfessionalScore(response.getProfessionalScore());
        entity.setLowScore(response.getLowScore());
        entity.setFollowUpRequired(response.getFollowUpRequired());
        entity.setSubmittedAt(response.getSubmittedAt());
        return entity;
    }
}
