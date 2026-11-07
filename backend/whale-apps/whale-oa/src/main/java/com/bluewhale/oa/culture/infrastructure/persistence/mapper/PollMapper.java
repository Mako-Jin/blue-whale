package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投票 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PollMapper extends BaseMapper<PollEntity> {
}
