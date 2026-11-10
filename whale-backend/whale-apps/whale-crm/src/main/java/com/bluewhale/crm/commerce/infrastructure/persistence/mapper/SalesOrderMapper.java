package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商务订单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrderEntity> {
}
