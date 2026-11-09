package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.StockInboundItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存入库单明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface StockInboundItemMapper extends BaseMapper<StockInboundItemEntity> {
}
