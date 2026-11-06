package com.bluewhale.party.fee.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.fee.infrastructure.persistence.entity.FeeRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党费标准 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface FeeRuleMapper extends BaseMapper<FeeRuleEntity> {
}
