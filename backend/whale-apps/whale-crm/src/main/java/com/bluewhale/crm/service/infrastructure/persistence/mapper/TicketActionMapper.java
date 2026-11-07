package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.TicketActionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单处理记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TicketActionMapper extends BaseMapper<TicketActionEntity> {
}
