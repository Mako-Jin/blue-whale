package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
}
