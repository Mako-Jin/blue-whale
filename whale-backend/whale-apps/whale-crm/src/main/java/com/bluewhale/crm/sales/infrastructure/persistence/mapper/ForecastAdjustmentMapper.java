package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.ForecastAdjustmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预测调整记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForecastAdjustmentMapper extends BaseMapper<ForecastAdjustmentEntity> {
}
