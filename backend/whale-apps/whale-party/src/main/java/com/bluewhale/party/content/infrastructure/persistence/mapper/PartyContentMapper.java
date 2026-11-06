package com.bluewhale.party.content.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.content.infrastructure.persistence.entity.PartyContentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党建内容 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PartyContentMapper extends BaseMapper<PartyContentEntity> {
}
