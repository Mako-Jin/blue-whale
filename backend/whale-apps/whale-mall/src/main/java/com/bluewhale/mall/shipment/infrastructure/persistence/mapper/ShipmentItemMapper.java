package com.bluewhale.mall.shipment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.shipment.infrastructure.persistence.entity.ShipmentItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发货单明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ShipmentItemMapper extends BaseMapper<ShipmentItemEntity> {
}
