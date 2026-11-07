package com.bluewhale.mall.cart.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.cart.infrastructure.persistence.entity.CartItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItemEntity> {
}
