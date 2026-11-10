package com.bluewhale.doc.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，doc 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致并覆盖 M2/M3 协作与审批列。
 *
 * @author BlueWhale
 */
class DocFlywayMigrationScriptTest {

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
                .map(DocFlywayMigrationScriptTest::versionOf)
                .collect(Collectors.toList());
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
     * 基线一致性：V1 建表集合与 init.sql 一致且覆盖协作/审批关键列
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V1 基线表集合一致且覆盖页面协作与审批列")
    void baselineShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> baselineTables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, baselineTables, "V1 基线与 init.sql 的建表集合不一致");
        assertEquals(18, baselineTables.size(), "doc 基线应包含 18 张表");
        for (String table : List.of("doc_space", "doc_space_member", "doc_page", "doc_template",
                "doc_page_share", "doc_page_version", "doc_page_attachment", "doc_comment",
                "doc_page_star", "doc_page_acl", "doc_tag", "doc_page_tag", "doc_page_link",
                "doc_page_activity", "doc_page_recent", "doc_comment_read", "audit_log", "api_log")) {
            assertTrue(baselineTables.contains(table), "基线必须包含表：" + table);
        }
        String baseline = Files.readString(MIGRATION_DIR.resolve("V1__baseline.sql"), StandardCharsets.UTF_8);
        for (String column : List.of("content_json", "revision", "approval_status", "trash_root_id")) {
            assertTrue(baseline.contains(column), "基线应包含协作/审批列：" + column);
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
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(Files.readString(path, StandardCharsets.UTF_8));
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }
}
