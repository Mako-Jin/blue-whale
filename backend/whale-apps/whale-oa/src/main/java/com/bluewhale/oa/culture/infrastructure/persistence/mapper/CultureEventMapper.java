package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.CultureEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文化动态 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CultureEventMapper extends BaseMapper<CultureEventEntity> {
}
