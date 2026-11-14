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
     * 审计归档水位与 MQ 去重表）、通过全局唯一 ID 关联的 RBAC 关系表，以及
     * 「一人多租户」用户-租户关系表（{@code sys_user_tenant}，跨租户且查询显式带 user_id）。</p>
     *
     * <p>另含调度锁表 {@code pdm_chg_job_lock}：锁键全局唯一、无租户维度，
     * 插件若自动追加 {@code tenant_id} 会因列不存在直接报 {@code Unknown column}。</p>
     *
     * <p>权限中心的平台级表同样无 {@code tenant_id} 列（2026-11-14 补）：
     * {@code sys_brand_config} 用 {@code target_type/target_id} 表达作用域，
     * {@code sys_oauth_client} 是平台级客户端注册，{@code sys_gateway_blacklist} /
     * {@code sys_gateway_route_override} 是网关全局策略，
     * {@code oauth2_authorization*} 是 OAuth2 协议会话（按 client_id 全局唯一）。</p>
     */
    private List<String> ignoreTables = List.of(
            "sys_tenant", "sys_permission", "sys_i18n_message",
            "sys_user_role", "sys_role_permission", "sys_user_permission", "sys_user_tenant",
            "audit_archive_watermark", "audit_mq_message",
            "pdm_chg_job_lock",
            "agent_fact", "agent_index_meta",
            "sys_brand_config", "sys_oauth_client",
            "sys_gateway_blacklist", "sys_gateway_route_override",
            "oauth2_authorization", "oauth2_authorization_consent");
}
