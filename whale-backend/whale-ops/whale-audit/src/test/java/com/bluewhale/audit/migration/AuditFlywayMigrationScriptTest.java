package com.bluewhale.audit.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移脚本基线校验（REQ-SEC-022，audit 库）：防止破坏性语句、命名漂移与基线表漏建。
 *
 * <p>校验项：
 * <ol>
 *   <li>脚本命名符合 Flyway 版本化约定（{@code V<版本>__<描述>.sql}），版本唯一且递增；</li>
 *   <li>脚本不含 {@code DROP} / {@code TRUNCATE} / {@code DELETE} 等破坏性语句（日志表 append-only）；</li>
 *   <li>{@code V1__baseline.sql} 建表集合与 {@code db/init.sql} 一致，避免空库与存量库 schema 漂移；</li>
 *   <li>基线包含审计与四通道日志的关键表。</li>
 * </ol>
 *
 * @author BlueWhale
 */
class AuditFlywayMigrationScriptTest {

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
     * 迁移脚本命名与目录完整性校验
     *
     * @throws IOException 读取脚本目录失败
     */
    @Test
    @DisplayName("迁移脚本按 Flyway 版本命名且目录非空")
    void shouldFollowFlywayNaming() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
        List<String> versions = new ArrayList<>();
        for (Path script : scripts) {
            String fileName = script.getFileName().toString();
            Matcher matcher = VERSIONED_NAME.matcher(fileName);
            assertTrue(matcher.matches(), "脚本命名不符合 V<版本>__<描述>.sql：" + fileName);
            versions.add(matcher.group(1));
        }
        Set<String> uniqueVersions = new HashSet<>(versions);
        assertEquals(uniqueVersions.size(), versions.size(), "版本号不可重复：" + versions);
        List<Long> ordered = versions.stream().map(Long::valueOf).collect(Collectors.toList());
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i) > ordered.get(i - 1), "脚本版本号必须严格递增：" + ordered);
        }
    }

    /**
     * 破坏性语句校验：迁移脚本不允许 DROP / TRUNCATE / DELETE
     *
     * <p>唯一豁免是 {@code DROP TRIGGER IF EXISTS}：MySQL 8 没有 {@code CREATE TRIGGER IF NOT EXISTS}，
     * 触发器类迁移只能「先删后建」才能重跑幂等；删除的是守卫对象而非数据，
     * 且紧接着一定会重建（V2 即此用法）。表级 {@code DROP} 仍一律禁止。</p>
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本不含 DROP / TRUNCATE / DELETE（DROP TRIGGER 幂等重建除外）")
    void shouldNotContainDestructiveStatements() throws IOException {
        for (Path script : migrationScripts()) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                if (statement.startsWith("DROP TRIGGER IF EXISTS ")) {
                    continue;
                }
                assertFalse(statement.startsWith("DROP "),
                        script.getFileName() + " 含 DROP 语句：" + line);
                assertFalse(statement.startsWith("TRUNCATE "),
                        script.getFileName() + " 含 TRUNCATE 语句：" + line);
                assertFalse(statement.startsWith("DELETE "),
                        script.getFileName() + " 含 DELETE 语句：" + line);
            }
        }
    }

    /**
     * 基线一致性校验：V1 建表集合必须与 init.sql 完全一致
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V1 基线建表集合与 init.sql 一致")
    void baselineTablesShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> baselineTables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, baselineTables, "V1 基线与 init.sql 的建表集合不一致");
        assertEquals(7, baselineTables.size(), "audit 基线应包含 7 张表");
        assertTrue(baselineTables.contains("audit_log"), "基线必须包含 audit_log");
        assertTrue(baselineTables.contains("audit_archive_watermark"), "基线必须包含归档水位表");
        for (String logTable : List.of("api_log", "outbound_log", "frontend_log", "behavior_log")) {
            assertTrue(baselineTables.contains(logTable), "基线必须包含多通道日志表：" + logTable);
        }
        assertTrue(baselineTables.contains("audit_mq_message"), "基线必须包含 MQ 去重表");
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
     * 解析脚本中的建表名集合
     *
     * @param path 脚本路径
     * @return 表名集合（保持出现顺序）
     * @throws IOException 读取失败
     */
    private static Set<String> extractTables(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(content);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }
}
