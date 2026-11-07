package com.bluewhale.mall.aftersale.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AfterSaleItemMapper extends BaseMapper<AfterSaleItemEntity> {
}
