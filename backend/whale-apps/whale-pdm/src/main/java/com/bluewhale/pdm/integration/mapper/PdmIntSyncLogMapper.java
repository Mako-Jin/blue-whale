package com.bluewhale.pdm.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.integration.entity.PdmIntSyncLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 集成同步日志 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmIntSyncLogMapper extends BaseMapper<PdmIntSyncLog> {
}
