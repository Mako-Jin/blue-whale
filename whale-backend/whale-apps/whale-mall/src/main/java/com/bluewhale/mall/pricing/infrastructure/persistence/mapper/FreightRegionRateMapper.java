package com.bluewhale.mall.pricing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.pricing.infrastructure.persistence.entity.FreightRegionRateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 区域运费费率 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface FreightRegionRateMapper extends BaseMapper<FreightRegionRateEntity> {
}
