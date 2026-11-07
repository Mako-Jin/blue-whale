package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SatisfactionSurveyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 满意度问卷 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SatisfactionSurveyMapper extends BaseMapper<SatisfactionSurveyEntity> {
}
