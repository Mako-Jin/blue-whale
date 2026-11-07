package com.bluewhale.crm.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.MarketingSendEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批量触达任务 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MarketingSendMapper extends BaseMapper<MarketingSendEntity> {
}
