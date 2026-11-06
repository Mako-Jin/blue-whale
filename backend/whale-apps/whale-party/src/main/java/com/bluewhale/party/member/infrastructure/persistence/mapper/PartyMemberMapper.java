package com.bluewhale.party.member.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.member.infrastructure.persistence.entity.PartyMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党员档案 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PartyMemberMapper extends BaseMapper<PartyMemberEntity> {
}
