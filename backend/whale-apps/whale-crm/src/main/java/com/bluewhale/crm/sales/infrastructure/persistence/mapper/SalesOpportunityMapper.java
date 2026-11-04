package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesOpportunityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商机 Mapper（sales 上下文）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesOpportunityMapper extends BaseMapper<SalesOpportunityEntity> {
}
