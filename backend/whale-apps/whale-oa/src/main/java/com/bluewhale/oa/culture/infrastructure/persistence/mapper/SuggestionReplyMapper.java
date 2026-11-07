package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.SuggestionReplyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 建议回复 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SuggestionReplyMapper extends BaseMapper<SuggestionReplyEntity> {
}
