package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.LeadActivityEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索活动 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface LeadActivityMapper extends BaseMapper<LeadActivityEntity> {
}
