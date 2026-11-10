package com.bluewhale.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.common.log.entity.ApiLogRecord;

/**
 * 后端接口日志 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface ApiLogMapper extends BaseMapper<ApiLogRecord> {
}
