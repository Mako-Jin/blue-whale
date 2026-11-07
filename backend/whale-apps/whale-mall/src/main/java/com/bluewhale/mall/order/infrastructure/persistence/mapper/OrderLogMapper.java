package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单状态流水 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderLogMapper extends BaseMapper<OrderLogEntity> {
}
