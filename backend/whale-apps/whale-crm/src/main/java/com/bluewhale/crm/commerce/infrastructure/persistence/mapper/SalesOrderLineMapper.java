package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商务订单行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesOrderLineMapper extends BaseMapper<SalesOrderLineEntity> {
}
