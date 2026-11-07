package com.bluewhale.crm.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.ActionRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行动计划规则 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ActionRuleMapper extends BaseMapper<ActionRuleEntity> {
}
