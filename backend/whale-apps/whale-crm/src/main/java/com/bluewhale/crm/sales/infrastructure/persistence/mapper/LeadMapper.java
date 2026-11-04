package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.LeadEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface LeadMapper extends BaseMapper<LeadEntity> {
}
