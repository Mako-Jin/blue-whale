package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderCouponEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商城订单用券明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderCouponMapper extends BaseMapper<OrderCouponEntity> {
}
