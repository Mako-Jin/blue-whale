package com.bluewhale.audit.retention.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.audit.retention.entity.AuditArchiveWatermark;

/**
 * 审计日志归档水位 Mapper。
 *
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface AuditArchiveWatermarkMapper extends BaseMapper<AuditArchiveWatermark> {
}
