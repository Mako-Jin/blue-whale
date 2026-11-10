package com.bluewhale.party.union.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工会会员 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface UnionMemberMapper extends BaseMapper<UnionMemberEntity> {
}
