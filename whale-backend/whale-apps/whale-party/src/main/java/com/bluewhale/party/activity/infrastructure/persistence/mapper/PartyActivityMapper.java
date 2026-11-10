package com.bluewhale.party.activity.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.activity.infrastructure.persistence.entity.PartyActivityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织生活 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PartyActivityMapper extends BaseMapper<PartyActivityEntity> {
}
