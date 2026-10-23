package com.bluewhale.audit.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.audit.mq.entity.AuditMqMessage;

/**
 * 审计 MQ 消息去重 Mapper（AUTH-10）。
 *
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface AuditMqMessageMapper extends BaseMapper<AuditMqMessage> {
}
