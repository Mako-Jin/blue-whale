package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesTargetAdjustmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售目标调整记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesTargetAdjustmentMapper extends BaseMapper<SalesTargetAdjustmentEntity> {
}
