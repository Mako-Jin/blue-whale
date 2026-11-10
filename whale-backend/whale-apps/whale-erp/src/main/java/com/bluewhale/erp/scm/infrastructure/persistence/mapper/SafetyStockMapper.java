package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SafetyStockEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全库存 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SafetyStockMapper extends BaseMapper<SafetyStockEntity> {
}
