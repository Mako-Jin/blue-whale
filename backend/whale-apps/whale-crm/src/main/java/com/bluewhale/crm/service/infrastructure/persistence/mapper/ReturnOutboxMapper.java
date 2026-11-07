package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退换货下发 Outbox Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ReturnOutboxMapper extends BaseMapper<ReturnOutboxEntity> {
}
