package com.bluewhale.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.common.log.entity.BehaviorLogRecord;

/**
 * 前端用户行为埋点 Mapper。
 * <p>由 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface BehaviorLogMapper extends BaseMapper<BehaviorLogRecord> {
}
