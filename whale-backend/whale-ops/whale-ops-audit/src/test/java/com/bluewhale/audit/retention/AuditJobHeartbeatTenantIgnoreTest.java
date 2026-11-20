package com.bluewhale.audit.retention;

import com.bluewhale.common.tenant.TenantSqlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计平台级表的租户隔离配置校验。
 *
 * <p>{@code audit_job_heartbeat} 与 {@code audit_archive_watermark} 都没有 {@code tenant_id} 列，
 * 但 MyBatis 租户插件是「全表默认加条件」——一旦漏在白名单外，心跳读写会直接
 * {@code Unknown column 'tenant_id' in 'where clause'}：作业每轮都失败，
 * 表面上却只是"心跳没更新"，排查成本极高（2026-11-14 品牌配置、2026-11-20 心跳表都踩过）。
 *
 * <p>这条断言钉住默认白名单，防止后续有人「顺手清理配置」。新增无 {@code tenant_id}
 * 的审计表时，这里也要加一行。</p>
 *
 * @author BlueWhale
 */
class AuditJobHeartbeatTenantIgnoreTest {

    @Test
    @DisplayName("审计平台级表默认排除在租户隔离之外，避免 Unknown column 'tenant_id'")
    void auditPlatformTablesShouldBeIgnoredByTenantPlugin() {
        TenantSqlProperties properties = new TenantSqlProperties();

        assertTrue(properties.getIgnoreTables().contains("audit_job_heartbeat"),
                "audit_job_heartbeat 无 tenant_id 列，必须在 whale.tenant.sql.ignore-tables 默认值里");
        assertTrue(properties.getIgnoreTables().contains("audit_archive_watermark"),
                "audit_archive_watermark 无 tenant_id 列，必须在白名单里");
    }
}
