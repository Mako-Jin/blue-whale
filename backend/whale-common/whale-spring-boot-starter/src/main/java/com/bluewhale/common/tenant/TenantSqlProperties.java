package com.bluewhale.common.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MyBatis 租户行级隔离配置（Wave 5.54 Phase 2）。
 *
 * <p>采用「显式表清单」而非默认全表：Phase 3 完成业务表 {@code tenant_id} 铺开前，
 * 未加列的表若被自动追加租户条件会直接 SQL 失败。业务表铺开后可将清单扩展至全部表。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.tenant.sql")
public class TenantSqlProperties {

    /** 是否启用租户行级 SQL 插件 */
    private boolean enabled = true;

    /** 参与自动租户隔离的表名（小写），默认覆盖已具备 tenant_id 列的 RBAC 与日志表 */
    private List<String> tables = List.of(
            "sys_user", "sys_role", "sys_menu", "sys_dept",
            "audit_log", "api_log", "outbound_log", "frontend_log", "behavior_log");
}
