package com.bluewhale.party.union.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionProposalEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 职代会提案 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface UnionProposalMapper extends BaseMapper<UnionProposalEntity> {
}
