package com.bluewhale.crm.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.MarketingSendItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批量触达明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MarketingSendItemMapper extends BaseMapper<MarketingSendItemEntity> {
}
