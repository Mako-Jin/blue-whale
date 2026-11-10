package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.OrderPushLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商务订单下发日志 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderPushLogMapper extends BaseMapper<OrderPushLogEntity> {
}
