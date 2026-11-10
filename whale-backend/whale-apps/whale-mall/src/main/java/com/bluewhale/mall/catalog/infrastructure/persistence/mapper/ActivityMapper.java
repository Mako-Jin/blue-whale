package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.ActivityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 限时特价活动 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ActivityMapper extends BaseMapper<ActivityEntity> {
}
