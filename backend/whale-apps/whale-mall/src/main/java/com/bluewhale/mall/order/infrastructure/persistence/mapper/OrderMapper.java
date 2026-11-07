package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
}
