package com.bluewhale.mall.shipment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.shipment.infrastructure.persistence.entity.ShipmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发货单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ShipmentMapper extends BaseMapper<ShipmentEntity> {
}
