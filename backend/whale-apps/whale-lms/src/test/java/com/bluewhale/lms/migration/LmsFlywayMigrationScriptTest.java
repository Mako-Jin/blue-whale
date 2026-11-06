package com.bluewhale.lms.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，lms 库）。
 *
 * <p>只允许建表与索引，禁止破坏性语句与业务种子数据；脚本必须可重复执行
 * （业务表建表语句必须带 {@code IF NOT EXISTS}）。</p>
 *
 * @author BlueWhale
 */
class LmsFlywayMigrationScriptTest {

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
    @DisplayName("业务表使用 lr_ 前缀且建表可重复执行")
    void businessTablesShouldUseLrPrefixAndBeRepeatable() throws IOException {
        for (Path script : migrationScripts()) {
            String content = Files.readString(script, StandardCharsets.UTF_8);
            Matcher matcher = CREATE_TABLE.matcher(content);
            while (matcher.find()) {
                String table = matcher.group(1);
                if (COMMON_TABLES.contains(table)) {
                    continue;
                }
                assertTrue(table.startsWith("lr_"), script.getFileName() + " 业务表缺 lr_ 前缀：" + table);
                assertTrue(content.contains("CREATE TABLE IF NOT EXISTS " + table + " ("),
                        script.getFileName() + " 建表缺 IF NOT EXISTS：" + table);
            }
        }
    }

    @Test
    @DisplayName("M1 覆盖五张核心表")
    void courseMigrationShouldCoverCoreTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("lr_course", "lr_course_chapter", "lr_courseware",
                "lr_enrollment", "lr_progress").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少核心表：" + expected);
    }

    @Test
    @DisplayName("M2 覆盖培训五张表")
    void trainingMigrationShouldCoverCoreTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("lr_training_class", "lr_class_session", "lr_assignment",
                "lr_checkin", "lr_lecturer_hours").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少培训核心表：" + expected);
    }

    @Test
    @DisplayName("M3-1 覆盖题库与试卷四张表")
    void examMigrationShouldCoverQuestionBankTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("lr_question", "lr_question_option",
                "lr_paper", "lr_paper_question").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少题库 / 试卷核心表：" + expected);
    }

    @Test
    @DisplayName("M3-2 覆盖考试与判分四张表")
    void examMigrationShouldCoverAttemptTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("lr_exam", "lr_exam_attempt",
                "lr_exam_answer", "lr_score").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少考试 / 判分核心表：" + expected);
    }

    /**
     * 列出迁移脚本。
     *
     * @return 脚本路径列表
     * @throws IOException 目录不可读
     */
    private List<Path> migrationScripts() throws IOException {        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
    }
}
