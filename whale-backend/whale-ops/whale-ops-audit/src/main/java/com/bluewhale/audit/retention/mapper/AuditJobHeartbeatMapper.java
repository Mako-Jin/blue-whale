package com.bluewhale.audit.retention.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.audit.retention.po.AuditJobHeartbeatPo;

/**
 * 审计作业心跳 Mapper。
 *
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * <p>表无 {@code tenant_id} 列（心跳是平台级运维证据，与租户无关），
 * 已在 {@code TenantSqlProperties.ignoreTables} 中排除，否则插件会追加
 * {@code tenant_id = ?} 导致 {@code Unknown column}。</p>
 *
 * @author BlueWhale
 */
public interface AuditJobHeartbeatMapper extends BaseMapper<AuditJobHeartbeatPo> {
}
