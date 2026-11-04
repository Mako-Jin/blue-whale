package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.OpportunityStageHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商机阶段历史 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OpportunityStageHistoryMapper extends BaseMapper<OpportunityStageHistoryEntity> {
}
