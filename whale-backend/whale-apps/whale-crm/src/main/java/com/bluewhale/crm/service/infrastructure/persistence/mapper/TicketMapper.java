package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.TicketEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TicketMapper extends BaseMapper<TicketEntity> {
}
