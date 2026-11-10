package com.bluewhale.message.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，message 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致。
 *
 * @author BlueWhale
 */
class MessageFlywayMigrationScriptTest {

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
                .map(MessageFlywayMigrationScriptTest::versionOf)
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
     * 基线一致性：全部迁移脚本建表集合与 init.sql 一致
     *
     * <p>V1 之后的增量脚本（如 V2 回执表）同样要回写 {@code init.sql}——手工空库初始化
     * 与 Flyway 增量部署必须收敛到同一份表结构，否则两条部署路径会分叉。</p>
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本建表集合与 init.sql 一致")
    void baselineShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> migratedTables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            migratedTables.addAll(extractTables(script));
        }
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, migratedTables, "迁移脚本与 init.sql 的建表集合不一致");
        assertEquals(8, migratedTables.size(), "消息服务 迁移脚本应包含 8 张表");
        for (String table : List.of("msg_template", "msg_record", "msg_delivery",
                "msg_announcement", "msg_receipt", "msg_callback_task")) {
            assertTrue(migratedTables.contains(table), "迁移脚本必须包含表：" + table);
        }

    }

    /**
     * OA M0 通知模板种子包含 5 个模板编码，且脚本无破坏性语句
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("OA M0 通知模板种子齐全")
    void oaM0NotifySeedShouldContainFiveTemplates() throws IOException {
        Path seed = Path.of("src/main/resources/db/migration-5.86-oa-m0-notify.sql");
        String script = Files.readString(seed, StandardCharsets.UTF_8);
        for (String code : List.of("OA_APPROVAL_TODO", "OA_APPROVAL_RESULT", "OA_APPROVAL_CC",
                "OA_APPROVAL_URGE", "OA_LEAVE_BALANCE")) {
            assertTrue(script.contains(code), "5.86 种子缺少模板：" + code);
        }
        for (String line : Files.readAllLines(seed, StandardCharsets.UTF_8)) {
            String statement = line.trim().toUpperCase();
            if (statement.startsWith("--") || statement.isEmpty()) {
                continue;
            }
            assertFalse(statement.startsWith("DROP "), "5.86 种子含 DROP 语句：" + line);
            assertFalse(statement.startsWith("TRUNCATE "), "5.86 种子含 TRUNCATE 语句：" + line);
            assertFalse(statement.startsWith("DELETE "), "5.86 种子含 DELETE 语句：" + line);
        }
    }

    /**
     * OA M1B-2b 出差报告提醒模板种子包含模板编码与幂等保护
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("OA M1B-2b 出差报告提醒模板种子齐全")
    void oaM1bTripReminderSeedShouldContainTemplate() throws IOException {
        Path seed = Path.of("src/main/resources/db/migration-5.86-oa-m1b-trip-reminder.sql");
        String script = Files.readString(seed, StandardCharsets.UTF_8);
        assertTrue(script.contains("OA_TRIP_REPORT_REMINDER"), "5.86 种子缺少模板：OA_TRIP_REPORT_REMINDER");
        assertTrue(script.contains("WHERE NOT EXISTS"), "模板种子必须使用 WHERE NOT EXISTS 保证幂等");
        for (String placeholder : List.of("${applicant}", "${destination}", "${endDate}",
                "${businessUrl}")) {
            assertTrue(script.contains(placeholder), "出差报告提醒模板缺少占位符：" + placeholder);
        }
        for (String line : Files.readAllLines(seed, StandardCharsets.UTF_8)) {
            String statement = line.trim().toUpperCase();
            if (statement.startsWith("--") || statement.isEmpty()) {
                continue;
            }
            assertFalse(statement.startsWith("DROP "), "5.86 种子含 DROP 语句：" + line);
            assertFalse(statement.startsWith("TRUNCATE "), "5.86 种子含 TRUNCATE 语句：" + line);
            assertFalse(statement.startsWith("DELETE "), "5.86 种子含 DELETE 语句：" + line);
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