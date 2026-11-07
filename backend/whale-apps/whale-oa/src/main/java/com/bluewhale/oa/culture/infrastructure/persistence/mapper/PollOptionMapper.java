package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollOptionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投票选项 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PollOptionMapper extends BaseMapper<PollOptionEntity> {
}
