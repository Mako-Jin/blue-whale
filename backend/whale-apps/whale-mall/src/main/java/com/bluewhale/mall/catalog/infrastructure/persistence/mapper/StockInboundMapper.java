package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.StockInboundEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存入库单头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface StockInboundMapper extends BaseMapper<StockInboundEntity> {
}
