package com.bluewhale.workflow.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移脚本基线校验（REQ-SEC-022，workflow 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * 全部迁移脚本建表并集与 {@code init.sql} 一致、关键增量列/索引/唯一键在 {@code init.sql} 中同步。
 *
 * @author BlueWhale
 */
class WorkflowFlywayMigrationScriptTest {

    /** 迁移脚本目录（相对模块根目录） */
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** 手工初始化脚本（与 V1 基线同源） */
    private static final Path INIT_SCRIPT = Path.of("src/main/resources/db/init.sql");

    /** Flyway 版本化脚本命名 */
    private static final Pattern VERSIONED_NAME = Pattern.compile("V(\\d+)__[A-Za-z0-9_]+.sql");

    /** 建表语句（兼容 init.sql 与 V1 的两种写法） */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)? (\\w+) \\(", Pattern.MULTILINE);

    /**
     * 版本命名、唯一递增与破坏性语句校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本命名合规且无破坏性语句")
    void scriptsShouldBeVersionedAndNonDestructive() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
        List<Long> versions = scripts.stream()
                .map(WorkflowFlywayMigrationScriptTest::versionOf)
                .sorted()
                .toList();
        assertEquals(versions.size(), Set.copyOf(versions).size(), "版本号不可重复：" + versions);
        for (int i = 1; i < versions.size(); i++) {
            assertTrue(versions.get(i) > versions.get(i - 1), "脚本版本号必须严格递增：" + versions);
        }
        for (Path script : scripts) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                assertFalse(statement.startsWith("DROP "), script.getFileName() + " 含 DROP 语句：" + line);
                assertFalse(statement.startsWith("TRUNCATE "), script.getFileName() + " 含 TRUNCATE 语句：" + line);
                assertFalse(statement.startsWith("DELETE "), script.getFileName() + " 含 DELETE 语句：" + line);
            }
        }
    }

    /**
     * 最终结构一致性：全部迁移脚本建表并集与 init.sql 一致
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("init.sql 与 Flyway 全量迁移后的表集合一致")
    void initScriptShouldMatchFlywayFinalTables() throws IOException {
        Set<String> flywayTables = new java.util.LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            flywayTables.addAll(extractTables(script));
        }
        Set<String> initTables = extractTables(INIT_SCRIPT);
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(flywayTables, initTables, "init.sql 与 Flyway 迁移后的表集合不一致");
        assertEquals(9, initTables.size(), "workflow 最终结构应包含 9 张表");
        for (String table : List.of("process_definition", "process_definition_version",
                "process_instance", "process_task", "api_log",
                "process_cc", "process_delegate", "process_urge", "audit_log")) {
            assertTrue(initTables.contains(table), "最终结构必须包含表：" + table);
        }
    }

    /**
     * 增量同步：V2~V13 新增的列 / 唯一键 / 索引必须全部体现在 init.sql
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("init.sql 同步 V2~V13 的增量列、唯一键与索引")
    void initScriptShouldCoverIncrementalChanges() throws IOException {
        String initSql = Files.readString(INIT_SCRIPT, StandardCharsets.UTF_8);
        for (String token : List.of("operator_id", "operator_type", "operator_source", "last_event_id",
                "assignee_overrides_json", "finished_at", "active_code", "window_bucket",
                "due_at", "idx_status_due_at",
                "uk_cc", "uk_tenant_active_code", "uk_urge_window", "idx_status_finished")) {
            assertTrue(initSql.contains(token), "init.sql 应同步增量结构：" + token);
        }
        String instanceBlock = initSql.substring(initSql.indexOf("CREATE TABLE process_instance"),
                initSql.indexOf("COMMENT='流程实例表'"));
        assertFalse(instanceBlock.contains("idx_status "),
                "process_instance 不应再建已被 idx_status_finished 覆盖的 idx_status");
        String v2 = Files.readString(MIGRATION_DIR.resolve("V2__task_operator.sql"), StandardCharsets.UTF_8);
        for (String column : List.of("operator_id", "operator_type", "operator_source")) {
            assertTrue(v2.contains(column), "V2 应补齐列：" + column);
        }
    }

    /**
     * V3 补齐抄送 / 代理 / 催办表与实例审批人覆盖列
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V3 补齐抄送 / 代理 / 催办表与实例审批人覆盖列")
    void v3ShouldAddCcDelegateAndUrge() throws IOException {
        Path v3 = MIGRATION_DIR.resolve("V3__cc_delegate_urge.sql");
        Set<String> tables = extractTables(v3);
        assertEquals(Set.of("process_cc", "process_delegate", "process_urge"), tables,
                "V3 应新增抄送 / 代理 / 催办 3 张表");
        String script = Files.readString(v3, StandardCharsets.UTF_8);
        assertTrue(script.contains("assignee_overrides_json"), "V3 应补齐实例审批人覆盖列");
        assertTrue(script.contains("uk_cc"), "抄送表应有唯一键约束");
    }

    /**
     * V4 种子 OA_LEAVE 定义及版本快照，且脚本无破坏性语句
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V4 种子 OA_LEAVE 定义与版本快照")
    void v4ShouldSeedOaLeaveDefinition() throws IOException {
        Path v4 = MIGRATION_DIR.resolve("V4__seed_oa_definitions.sql");
        String script = Files.readString(v4, StandardCharsets.UTF_8);
        assertTrue(script.contains("OA_LEAVE"), "V4 应种子 OA_LEAVE 流程定义");
        assertTrue(script.contains("'OA 请假审批'"), "V4 应包含 OA_LEAVE 定义名称");
        assertTrue(script.contains("stateMachine"), "V4 定义应为规范化 stateMachine 模型");
        assertTrue(script.contains("process_definition_version"), "V4 应同步写入版本快照");
        assertTrue(script.contains("publish_status"), "快照应写入发布状态，与定义服务 create 一致");
        for (String line : Files.readAllLines(v4, StandardCharsets.UTF_8)) {
            String statement = line.trim().toUpperCase();
            if (statement.startsWith("--") || statement.isEmpty()) {
                continue;
            }
            assertFalse(statement.startsWith("DROP "), "V4 含 DROP 语句：" + line);
            assertFalse(statement.startsWith("TRUNCATE "), "V4 含 TRUNCATE 语句：" + line);
            assertFalse(statement.startsWith("DELETE "), "V4 含 DELETE 语句：" + line);
        }
    }

    /**
     * 列出迁移脚本并按文件名字典序返回
     *
     * @return 迁移脚本路径
     * @throws IOException 读取目录失败
     */
    private static List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 解析脚本版本号
     *
     * @param script 脚本路径
     * @return 版本号
     */
    private static Long versionOf(Path script) {
        Matcher matcher = VERSIONED_NAME.matcher(script.getFileName().toString());
        assertTrue(matcher.matches(), "脚本命名不符合 V<版本>__<描述>.sql：" + script.getFileName());
        return Long.valueOf(matcher.group(1));
    }

    /**
     * 解析脚本中的建表名集合
     *
     * @param path 脚本路径
     * @return 表名集合（保持出现顺序）
     * @throws IOException 读取失败
     */
    private static Set<String> extractTables(Path path) throws IOException {
        Set<String> tables = new java.util.LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(Files.readString(path, StandardCharsets.UTF_8));
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }
}
