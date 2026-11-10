package com.bluewhale.config.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，config 库）：防止破坏性语句、命名漂移与基线表漏建。
 *
 * @author BlueWhale
 */
class ConfigFlywayMigrationScriptTest {

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
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本不含 DROP / TRUNCATE / DELETE")
    void shouldNotContainDestructiveStatements() throws IOException {
        for (Path script : migrationScripts()) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
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
        assertEquals(4, baselineTables.size(), "config 基线应包含 4 张表");
        assertTrue(baselineTables.contains("config_item"), "基线必须包含配置项表");
        assertTrue(baselineTables.contains("config_item_history"), "基线必须包含配置变更历史表");
        assertTrue(baselineTables.contains("audit_log"), "基线必须包含审计日志表");
        assertTrue(baselineTables.contains("api_log"), "基线必须包含接口日志表");
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
