package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.SuggestionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 建议 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SuggestionMapper extends BaseMapper<SuggestionEntity> {
}
