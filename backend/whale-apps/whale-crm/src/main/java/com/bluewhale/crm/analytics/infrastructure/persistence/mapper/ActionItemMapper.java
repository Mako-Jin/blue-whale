package com.bluewhale.crm.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.ActionItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行动项 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ActionItemMapper extends BaseMapper<ActionItemEntity> {
}
