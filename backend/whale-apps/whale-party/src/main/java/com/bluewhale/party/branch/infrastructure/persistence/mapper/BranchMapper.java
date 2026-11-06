package com.bluewhale.party.branch.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.branch.infrastructure.persistence.entity.BranchEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支部 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface BranchMapper extends BaseMapper<BranchEntity> {
}
