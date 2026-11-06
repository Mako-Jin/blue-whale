package com.bluewhale.party.migration;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移脚本基线校验（REQ-SEC-022，party 库）。
 *
 * <p>只允许建表与索引，禁止破坏性语句与业务种子数据；脚本必须可重复执行
 * （业务表建表语句必须带 {@code IF NOT EXISTS}）。</p>
 *
 * @author BlueWhale
 */
class PartyFlywayMigrationScriptTest {

    /** 迁移脚本目录 */
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** 建表语句 */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)? (\\w+) \\(", Pattern.MULTILINE);

    /** 非业务表（审计与接口日志） */
    private static final Set<String> COMMON_TABLES = Set.of("audit_log", "api_log");

    @Test
    @DisplayName("迁移脚本无破坏性语句")
    void scriptsShouldBeNonDestructive() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
        for (Path script : scripts) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                assertFalse(statement.startsWith("DROP "), script.getFileName() + " 含 DROP 语句：" + line);
                assertFalse(statement.startsWith("TRUNCATE "), script.getFileName() + " 含 TRUNCATE 语句：" + line);
                assertFalse(statement.startsWith("DELETE "), script.getFileName() + " 含 DELETE 语句：" + line);
                assertFalse(statement.startsWith("UPDATE "), script.getFileName() + " 含 UPDATE 语句：" + line);
            }
        }
    }

    @Test
    @DisplayName("迁移脚本不含业务种子数据")
    void scriptsShouldNotSeedBusinessData() throws IOException {
        for (Path script : migrationScripts()) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                assertFalse(statement.startsWith("INSERT INTO"), script.getFileName() + " 含 INSERT 种子：" + line);
            }
        }
    }

    @Test
    @DisplayName("业务表使用 pty_ 前缀且建表可重复执行")
    void businessTablesShouldUsePtyPrefixAndBeRepeatable() throws IOException {
        for (Path script : migrationScripts()) {
            String content = Files.readString(script, StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(content);
            while (matcher.find()) {
                String table = matcher.group(1);
                if (COMMON_TABLES.contains(table)) {
                    continue;
                }
                assertTrue(table.startsWith("pty_"), script.getFileName() + " 业务表缺 pty_ 前缀：" + table);
                assertTrue(content.contains("CREATE TABLE IF NOT EXISTS " + table + " ("),
                        script.getFileName() + " 建表缺 IF NOT EXISTS：" + table);
            }
        }
    }

    @Test
    @DisplayName("M-P0 覆盖编号流水表")
    void platformMigrationShouldCoverSequenceTable() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("pty_sequence").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少编号流水表：" + expected);
    }

    @Test
    @DisplayName("编号流水表唯一键不含 deleted（逻辑删除会让取号分裂）")
    void sequenceUniqueKeyShouldNotIncludeDeleted() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V2__platform.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pty_sequence (tenant_id, seq_key, seq_date)"),
                "pty_sequence 唯一键应为 (tenant_id, seq_key, seq_date)，不含 deleted");
    }

    /**
     * 列出迁移脚本。
     *
     * @return 脚本路径列表
     * @throws IOException 目录不可读
     */
    private List<Path> migrationScripts() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
    }
}
