package com.bluewhale.mall.aftersale.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSaleEntity> {
}
