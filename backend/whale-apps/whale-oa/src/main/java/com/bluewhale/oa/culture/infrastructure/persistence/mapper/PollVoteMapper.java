package com.bluewhale.oa.culture.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollVoteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投票记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PollVoteMapper extends BaseMapper<PollVoteEntity> {
}
