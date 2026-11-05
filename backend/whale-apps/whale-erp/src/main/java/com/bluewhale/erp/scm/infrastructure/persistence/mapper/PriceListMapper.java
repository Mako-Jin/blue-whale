package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PriceListEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 价目表 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PriceListMapper extends BaseMapper<PriceListEntity> {
}
