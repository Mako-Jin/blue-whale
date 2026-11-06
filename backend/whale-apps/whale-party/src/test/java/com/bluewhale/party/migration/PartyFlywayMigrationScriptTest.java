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
    @DisplayName("M-P1 覆盖党建台账六张表")
    void ledgerMigrationShouldCoverPartyLedgerTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("pty_branch", "pty_member", "pty_activity",
                "pty_activity_signin", "pty_fee_rule", "pty_fee_bill").collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少党建台账表：" + expected);
    }

    @Test
    @DisplayName("一人一档与月度账单唯一键覆盖到位")
    void ledgerUniqueKeysShouldGuardDuplicates() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V3__party_ledger.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pty_member_employee (tenant_id, employee_no)"),
                "党员档案须按员工编号做一人一档");
        assertTrue(content.contains("uk_pty_fee_bill (tenant_id, member_id, period_month)"),
                "党费账单须按人+月唯一，避免同月重复生成");
        assertTrue(content.contains("uk_pty_activity_signin (tenant_id, activity_id, member_id)"),
                "签到须按场次+人唯一，否则实到人数重复计数");
    }

    @Test
    @DisplayName("M-P2 覆盖党建内容三张表")
    void contentMigrationShouldCoverContentTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("pty_content", "pty_content_audit", "pty_content_read")
                .collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少党建内容表：" + expected);
    }

    @Test
    @DisplayName("阅读记录按人+内容唯一，阅读数不会被刷新放大")
    void contentReadUniqueKeyShouldGuardDuplicates() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V4__party_content.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pty_content_read (tenant_id, content_id, user_id)"),
                "阅读记录须按租户+内容+用户唯一");
    }

    @Test
    @DisplayName("M-P3 覆盖工会七张表")
    void unionMigrationShouldCoverUnionTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("pty_union_member", "pty_union_relief", "pty_union_welfare",
                "pty_union_welfare_item", "pty_union_activity", "pty_union_signup", "pty_union_proposal")
                .collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少工会表：" + expected);
    }

    @Test
    @DisplayName("会员编号与发放/报名/提案唯一键覆盖到位，且员工编号刻意不唯一")
    void unionUniqueKeysShouldMatchBusinessRules() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V5__party_union.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pty_union_member_no (tenant_id, member_no)"),
                "工会会员编号须租户内唯一");
        assertTrue(content.contains("uk_pty_union_welfare_item (tenant_id, welfare_id, union_member_id)"),
                "福利明细须按批次+会员唯一，重复发放才能幂等跳过");
        assertTrue(content.contains("uk_pty_union_signup (tenant_id, activity_id, union_member_id)"),
                "报名须按活动+会员唯一，取消后再报名不能新增行");
        assertTrue(content.contains("uk_pty_union_proposal_no (tenant_id, proposal_no)"),
                "提案编号须租户内唯一");
        assertFalse(content.contains("UNIQUE KEY uk_pty_union_member_employee"),
                "工会会员不得按员工编号建唯一键——退会后可再入会，唯一键会拒绝正常再入会");
    }

    @Test
    @DisplayName("编号流水表唯一键不含 deleted（逻辑删除会让取号分裂）")
    void sequenceUniqueKeyShouldNotIncludeDeleted() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V2__platform.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pty_sequence (tenant_id, seq_key, seq_date)"),
                "pty_sequence 唯一键应为 (tenant_id, seq_key, seq_date)，不含 deleted");
    }

    @Test
    @DisplayName("M-P4 覆盖统计快照、通知留痕与员工快照三张表")
    void metricMigrationShouldCoverAnalyticsTables() throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        for (Path script : migrationScripts()) {
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        Set<String> expected = Stream.of("pty_metric_snapshot", "pty_notify_log", "pty_employee_snapshot")
                .collect(Collectors.toSet());
        assertTrue(tables.containsAll(expected), "缺少统计与集成表：" + expected);
    }

    @Test
    @DisplayName("快照覆盖写与通知幂等唯一键到位，党员档案追加出生日期与学历")
    void metricUniqueKeysShouldMatchBusinessRules() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V6__party_metric.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains(
                "uk_pty_metric_snapshot (tenant_id, stat_date, metric_type, scope_type, scope_id)"),
                "统计快照须按租户+统计日+指标+范围唯一，重刷才能覆盖写不追加");
        assertTrue(content.contains("uk_pty_notify_log (tenant_id, idempotency_key, user_id)"),
                "通知留痕须按租户+幂等键+接收人唯一，同一提醒不重发");
        assertTrue(content.contains("uk_pty_employee_snapshot (tenant_id, employee_no)"),
                "员工快照须按租户+工号唯一，同步按工号 upsert");
        assertTrue(content.contains("ADD COLUMN birth_date DATE"),
                "pty_member 须追加出生日期（年龄结构统计）");
        assertTrue(content.contains("ADD COLUMN education"),
                "pty_member 须追加学历编码（学历结构统计）");
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
