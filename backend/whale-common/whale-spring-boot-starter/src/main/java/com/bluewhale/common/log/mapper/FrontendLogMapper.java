package com.bluewhale.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.common.log.entity.FrontendLogRecord;

/**
 * 前端开发者日志 Mapper。
 * <p>由 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface FrontendLogMapper extends BaseMapper<FrontendLogRecord> {
}
