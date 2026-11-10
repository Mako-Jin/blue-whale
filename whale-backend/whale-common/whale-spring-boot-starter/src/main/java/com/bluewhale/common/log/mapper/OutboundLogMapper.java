package com.bluewhale.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.common.log.entity.OutboundLogRecord;

/**
 * 后端出站三方调用日志 Mapper。
 * <p>由 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface OutboundLogMapper extends BaseMapper<OutboundLogRecord> {
}
