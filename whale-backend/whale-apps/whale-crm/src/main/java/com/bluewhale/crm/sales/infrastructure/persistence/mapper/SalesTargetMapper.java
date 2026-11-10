package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesTargetEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售目标 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesTargetMapper extends BaseMapper<SalesTargetEntity> {
}
