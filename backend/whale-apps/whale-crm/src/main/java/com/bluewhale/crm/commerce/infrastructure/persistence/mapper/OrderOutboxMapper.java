package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.OrderOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 领域事件 Outbox Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderOutboxMapper extends BaseMapper<OrderOutboxEntity> {
}
