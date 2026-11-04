package com.bluewhale.mes.common.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mes.common.outbox.entity.MesOutboxEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 集成发件箱 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface MesOutboxEventMapper extends BaseMapper<MesOutboxEvent> {
}
