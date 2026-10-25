package com.bluewhale.common.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MyBatis 租户行级隔离配置（Wave 5.54 Phase 3）。
 *
 * <p>Phase 3 已完成业务表 {@code tenant_id} 铺开，插件切换为「全表默认 + 忽略白名单」：
 * 除白名单中的平台共享表/关联表外，所有表都会自动追加租户条件。无租户上下文的线程
 * （启动期、未传播的后台任务）一律忽略，避免在无租户语义的链路中静默写入错误租户。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.tenant.sql")
public class TenantSqlProperties {

    /** 是否启用租户行级 SQL 插件 */
    private boolean enabled = true;

    /**
     * 不参与自动租户隔离的表名（小写）。
     *
     * <p>包括：平台共享表（租户清单、权限点、国际化词条、Agent 平台知识/索引元数据、
     * 审计归档水位与 MQ 去重表）以及通过全局唯一 ID 关联的 RBAC 关系表。</p>
     */
    private List<String> ignoreTables = List.of(
            "sys_tenant", "sys_permission", "sys_i18n_message",
            "sys_user_role", "sys_role_permission", "sys_user_permission",
            "audit_archive_watermark", "audit_mq_message",
            "agent_fact", "agent_index_meta");
}
