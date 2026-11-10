package com.bluewhale.job.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Flyway 迁移策略：起服前清理双线合并遗留的重复脚本历史行。
 *
 * <p>2026-11-09 双线合并时，本地与远程各自从同一 merge-base 递增出 V2/V3/V4，
 * 版本号三处撞车。远程那三份（V2 种子 / V3 audit_log / V4 种子）与本地已有能力
 * 完全重复，脚本已删除。但 <b>删脚本不会删库里的历史行</b>：应用过远程脚本的环境
 * 会停在「已应用 V2/V3/V4、本地却找不到同名脚本」的状态，Flyway 校验直接拒启。</p>
 *
 * <p>更隐蔽的后果是：远程的 V2 只做种子、不建表，本地 V2 才创建
 * {@code job_executor} 表并给 {@code job_info} 补 {@code executor_app}/{@code job_code}
 * 两列。历史行占着 V2，本地 V2 就永远不会执行 —— 服务能起也会在运行期报列不存在。
 * 因此这里不是简单地「忽略缺失」，而是把这 3 行删掉，让本地 V2/V3/V4 正常补跑：
 * 建表用 {@code IF NOT EXISTS}，种子用 {@code WHERE NOT EXISTS(job_code)} 判重，
 * 补跑对已有数据无副作用。</p>
 *
 * <p>仅当历史表里出现这 3 个脚本名时才动手，未受影响的环境（新库、本地开发库）
 * 完全走 Flyway 默认路径，不会静默重建任何历史。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Configuration
public class FlywayLegacyRepairConfig {

    /**
     * 合并时删除的远程重复脚本（与本地 V2/V3/V4 版本号撞车）。
     * 匹配用后缀 LIKE，兼容历史表里存相对路径或绝对路径的实现差异。
     */
    private static final List<String> LEGACY_SCRIPTS = List.of(
            "V2__seed_crm_remote_jobs.sql",
            "V3__audit_log.sql",
            "V4__seed_crm_sync_jobs.sql");

    /** 受影响版本号：清理遗留脚本行时顺带清掉这些版本上失败的残行（等价于 flyway repair） */
    private static final List<String> LEGACY_VERSIONS = List.of("2", "3", "4");

    /**
     * 替换 Spring Boot 默认的「直接 migrate」，改为「先清理遗留行再 migrate」。
     *
     * @return 迁移策略
     */
    @Bean
    public FlywayMigrationStrategy legacyAwareMigrationStrategy() {
        return flyway -> {
            purgeLegacyHistory(flyway);
            flyway.migrate();
        };
    }

    /**
     * 删除遗留脚本对应的历史行；历史表不存在（全新库）时直接跳过。
     *
     * <p>清理失败不阻断启动：交由 Flyway 自己报出原始错误，避免把真实问题
     * 掩盖成一条 warn 日志。</p>
     *
     * @param flyway Flyway 实例
     */
    private void purgeLegacyHistory(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        if (dataSource == null) {
            return;
        }
        String table = flyway.getConfiguration().getTable();
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, table)) {
                return;
            }
            List<String> scripts = findStaleScripts(connection, table);
            int deleted = deleteStaleRows(connection, table);
            if (deleted > 0) {
                log.warn("检测到双线合并遗留的重复迁移历史，已清理 {} 行（{}），"
                                + "本次将补跑本地 V2/V3/V4 恢复 job_executor 表与 job_info.executor_app/job_code 列",
                        deleted, scripts);
            }
        } catch (SQLException ex) {
            log.error("清理遗留 Flyway 历史行失败，交由 Flyway 继续校验（如需人工介入请执行 flyway repair）", ex);
        }
    }

    /**
     * 判断 Flyway 历史表是否存在。
     *
     * @param connection 数据库连接
     * @param table      历史表名
     * @return 存在返回 true
     * @throws SQLException 查询失败
     */
    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 查出遗留脚本对应的历史行（仅用于日志留痕，方便事后核对）。
     *
     * @param connection 数据库连接
     * @param table      历史表名
     * @return 命中的脚本名列表
     * @throws SQLException 查询失败
     */
    private List<String> findStaleScripts(Connection connection, String table) throws SQLException {
        List<String> scripts = new ArrayList<>();
        String sql = "SELECT script FROM " + table + " WHERE " + legacyScriptCondition();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindLegacyScripts(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scripts.add(rs.getString(1));
                }
            }
        }
        return scripts;
    }

    /**
     * 删除遗留脚本行，以及这些版本上失败的历史行。
     *
     * @param connection 数据库连接
     * @param table      历史表名
     * @return 删除行数
     * @throws SQLException 删除失败
     */
    private int deleteStaleRows(Connection connection, String table) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT version FROM " + table + " WHERE " + legacyScriptCondition()
                        + " OR (success = 0 AND version IN (?, ?, ?))")) {
            int index = bindLegacyScripts(ps, 1);
            for (String version : LEGACY_VERSIONS) {
                ps.setString(index++, version);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versions.add(rs.getString(1));
                }
            }
        }
        if (versions.isEmpty()) {
            return 0;
        }
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + legacyScriptCondition()
                        + " OR (success = 0 AND version IN (?, ?, ?))")) {
            int index = bindLegacyScripts(del, 1);
            for (String version : LEGACY_VERSIONS) {
                del.setString(index++, version);
            }
            return del.executeUpdate();
        }
    }

    /**
     * 遗留脚本的匹配条件（后缀 LIKE，避免路径前缀差异漏匹配）。
     *
     * @return SQL 条件片段
     */
    private String legacyScriptCondition() {
        return String.join(" OR ", LEGACY_SCRIPTS.stream().map(s -> "script LIKE ?").toList());
    }

    /**
     * 按顺序绑定遗留脚本的 LIKE 参数。
     *
     * @param statement 预编译语句
     * @param from      起始占位符序号
     * @return 下一个可用占位符序号
     * @throws SQLException 绑定失败
     */
    private int bindLegacyScripts(PreparedStatement statement, int from) throws SQLException {
        int index = from;
        for (String script : LEGACY_SCRIPTS) {
            statement.setString(index++, "%" + script);
        }
        return index;
    }
}
